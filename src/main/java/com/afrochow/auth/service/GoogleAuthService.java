package com.afrochow.auth.service;

import com.afrochow.auth.dto.LoginResponseDto;
import com.afrochow.common.enums.AuthProvider;
import com.afrochow.common.enums.Role;
import com.afrochow.common.exceptions.CustomerWaitlistModeException;
import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.security.JwtTokenProvider;
import com.afrochow.security.service.RefreshTokenService;
import com.afrochow.security.util.CookieConstants;
import com.afrochow.security.util.CookieUtils;
import com.afrochow.user.mapper.UserMapper;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
public class GoogleAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final OutboxEventService outboxEventService;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final String cookieDomain;
    private final HttpClient httpClient;
    private final boolean customerWaitlistMode;

    private GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleAuthService(
            UserRepository userRepository,
            UserMapper userMapper,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            OutboxEventService outboxEventService,
            @Value("${google.client-id}")     String googleClientId,
            @Value("${google.client-secret}") String googleClientSecret,
            @Value("${google.redirect-uri}")  String googleRedirectUri,
            @Value("${app.cookie.domain:}")   String cookieDomain,
            @Value("${app.customer-waitlist-mode:true}") boolean customerWaitlistMode
    ) {
        this(userRepository, userMapper, jwtTokenProvider, refreshTokenService, outboxEventService,
                googleClientId, googleClientSecret, googleRedirectUri, cookieDomain, customerWaitlistMode,
                HttpClient.newHttpClient());
    }

    public GoogleAuthService(
            UserRepository userRepository,
            UserMapper userMapper,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            OutboxEventService outboxEventService,
            String googleClientId,
            String googleClientSecret,
            String googleRedirectUri,
            String cookieDomain,
            boolean customerWaitlistMode,
            HttpClient httpClient
    ) {
        this.userRepository      = userRepository;
        this.userMapper          = userMapper;
        this.jwtTokenProvider    = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.outboxEventService  = outboxEventService;
        this.googleClientId      = googleClientId;
        this.googleClientSecret  = googleClientSecret;
        this.googleRedirectUri   = googleRedirectUri;
        this.cookieDomain        = cookieDomain;
        this.httpClient          = httpClient;
        this.customerWaitlistMode = customerWaitlistMode;
    }

    @PostConstruct
    void initVerifier() {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public LoginResponseDto authenticateWithGoogle(
            String code,
            String context,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String idToken = exchangeCodeForIdToken(code);
        GoogleIdToken.Payload payload = verifyToken(idToken);

        String email      = payload.getEmail();
        String googleId   = payload.getSubject();
        String firstName  = (String) payload.get("given_name");
        String lastName   = (String) payload.get("family_name");
        String pictureUrl = (String) payload.get("picture");

        if (firstName == null || firstName.isBlank()) firstName = email.split("@")[0];
        if (lastName  == null || lastName.isBlank())  lastName  = ".";

        User user = findOrCreateCustomer(email, googleId, firstName, lastName, pictureUrl, context);

        String accessToken  = jwtTokenProvider.createToken(user);
        String refreshToken = refreshTokenService.createRefreshTokenForUser(user, httpRequest);

        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.ACCESS_TOKEN_COOKIE,
                accessToken, jwtTokenProvider.getAccessTokenExpirationSeconds(),
                true, "None", cookieDomain);
        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE,
                refreshToken, refreshTokenService.getRefreshTokenExpirationSeconds(),
                true, "None", cookieDomain);

        // Stamp last login time
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        log.info("google.login.success email={} publicUserId={}", email, user.getPublicUserId());
        return buildLoginResponse(user);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String exchangeCodeForIdToken(String code) {
        String body = "code="           + encode(code)
                + "&client_id="     + encode(googleClientId)
                + "&client_secret=" + encode(googleClientSecret)
                + "&redirect_uri="  + encode(googleRedirectUri)
                + "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GOOGLE_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("google.token.exchange.failed status={} body={}", response.statusCode(), response.body());
                throw new IllegalArgumentException("Failed to exchange authorization code");
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (!json.has("id_token")) {
                log.error("google.token.exchange.missing_id_token body={}", response.body());
                throw new IllegalArgumentException("No id_token in Google token response");
            }

            return json.get("id_token").getAsString();

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("google.token.exchange.error: {}", e.getMessage());
            throw new IllegalArgumentException("Google token exchange failed");
        }
    }

    private GoogleIdToken.Payload verifyToken(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new IllegalArgumentException("Invalid Google token");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            log.error("google.token.verification.failed: {}", e.getMessage());
            throw new IllegalArgumentException("Google token verification failed");
        }
    }

    private User findOrCreateCustomer(
            String email, String googleId,
            String firstName, String lastName, String pictureUrl,
            String context
    ) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepository.save(user);
            }
            return user;  // returning user, any role — no welcome email, already sent at registration
        }

        // No existing account — this is a new sign-up, not a sign-in.
        if ("vendor".equalsIgnoreCase(context)) {
            // Google was clicked from the vendor registration flow's "Already have
            // an account? Sign in," not the general customer entry point. Google
            // can only ever produce a CUSTOMER account (no business name/address/
            // etc. to build a vendor profile from), so an unknown email here must
            // NOT be silently turned into a mismatched customer account — that
            // would strand someone who came in to register as a vendor. Regardless
            // of waitlist mode.
            throw new ResourceNotFoundException(
                    "No account found for that Google email. Please complete vendor registration below.");
        }

        // Customer entry point. Google auth has no client-side waitlist gate
        // (unlike the manual sign-up form), so this is the only place that can
        // block it: an unknown email must not silently mint a real CUSTOMER
        // account while waitlist mode is active.
        if (customerWaitlistMode) {
            throw new CustomerWaitlistModeException(
                    "Afrochow ordering is opening soon. Join the waitlist and we will let you know when customer accounts go live.");
        }

        // Generate username upfront — publicUserId and username are both generated
        // in @PrePersist which fires at flush time. saveAndFlush() below ensures they
        // are populated on the returned entity before createToken() is called.
        String base = (firstName + lastName).toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.length() < 3) base = base + "user";
        String username = base.substring(0, Math.min(base.length(), 16))
                + (SECURE_RANDOM.nextInt(9000) + 1000);

        User user = User.builder()
                .email(email)
                .username(username)
                .googleId(googleId)
                .firstName(firstName)
                .lastName(lastName)
                .password(null)
                .role(Role.CUSTOMER)
                .authProvider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .acceptTerms(true)
                .isActive(true)
                .profileImageUrl(pictureUrl)
                .build();

        CustomerProfile customerProfile = CustomerProfile.builder().build();
        customerProfile.setUser(user);
        user.setCustomerProfile(customerProfile);

        // saveAndFlush forces immediate DB flush → @PrePersist fires →
        // publicUserId is set on the entity before createToken() is called.
        User savedUser = userRepository.saveAndFlush(user);

        // Fire the same welcome email + in-app notification that regular
        // registration triggers after email verification. Google users have
        // their email pre-verified so we fire it immediately on first sign-in.
        outboxEventService.userRegistered(
                savedUser.getPublicUserId(),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getRole().name()
        );

        return savedUser;
    }

    private LoginResponseDto buildLoginResponse(User user) {
        return userMapper.toLoginResponse(user);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
