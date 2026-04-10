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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    @Transactional
    public LoginResponse authenticateWithGoogle(
            String credential,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        GoogleIdToken.Payload payload = verifyToken(credential);

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
                accessToken, jwtTokenProvider.getAccessTokenExpirationSeconds(), true, "None", cookieDomain);
        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE,
                refreshToken, refreshTokenService.getRefreshTokenExpirationSeconds(), true, "None", cookieDomain);

        log.info("google.login.success email={} publicUserId={}", email, user.getPublicUserId());
        return buildLoginResponse(user);
    }

    private GoogleIdToken.Payload verifyToken(String credential) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google token");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
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

        User user = User.builder()
                .email(email)
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

        return userRepository.save(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        return LoginResponse.builder()
                .publicUserId(user.getPublicUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
