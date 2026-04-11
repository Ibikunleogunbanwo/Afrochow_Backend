package com.afrochow.auth.service;

import com.afrochow.auth.dto.LoginResponse;
import com.afrochow.common.enums.Role;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.security.JwtTokenProvider;
import com.afrochow.security.Services.RefreshTokenService;
import com.afrochow.security.Utils.CookieConstants;
import com.afrochow.security.Utils.CookieUtils;
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
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final String cookieDomain;

    private final HttpClient httpClient;
    private GoogleIdTokenVerifier verifier;

    public GoogleAuthService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            @Value("${google.client-id}")      String googleClientId,
            @Value("${google.client-secret}")  String googleClientSecret,
            @Value("${google.redirect-uri}")   String googleRedirectUri,
            @Value("${app.cookie.domain:}")    String cookieDomain
    ) {
        this.userRepository      = userRepository;
        this.jwtTokenProvider    = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.googleClientId      = googleClientId;
        this.googleClientSecret  = googleClientSecret;
        this.googleRedirectUri   = googleRedirectUri;
        this.cookieDomain        = cookieDomain;
        this.httpClient          = HttpClient.newHttpClient();
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
    public LoginResponse authenticateWithGoogle(
            String code,
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

        User user = findOrCreateCustomer(email, googleId, firstName, lastName, pictureUrl);

        String accessToken  = jwtTokenProvider.createToken(user);
        String refreshToken = refreshTokenService.createRefreshTokenForUser(user, httpRequest);

        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.ACCESS_TOKEN_COOKIE,
                accessToken, jwtTokenProvider.getAccessTokenExpirationSeconds(),
                true, "None", cookieDomain);
        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE,
                refreshToken, refreshTokenService.getRefreshTokenExpirationSeconds(),
                true, "None", cookieDomain);

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
            String firstName, String lastName, String pictureUrl
    ) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepository.save(user);
            }
            return user;
        }

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
                .emailVerified(true)
                .acceptTerms(true)
                .isActive(true)
                .profileImageUrl(pictureUrl)
                .build();

        CustomerProfile customerProfile = CustomerProfile.builder().build();
        customerProfile.setUser(user);
        user.setCustomerProfile(customerProfile);

        return userRepository.saveAndFlush(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        boolean profileComplete = user.getPhone() != null && !user.getPhone().isBlank();
        return LoginResponse.builder()
                .publicUserId(user.getPublicUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isProfileComplete(profileComplete)
                .build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}