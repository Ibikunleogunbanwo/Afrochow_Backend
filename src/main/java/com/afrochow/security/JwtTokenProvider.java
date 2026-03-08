package com.afrochow.security;
import com.afrochow.user.model.User;
import com.afrochow.security.Utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Spring-aware JWT provider that delegates all crypto work to JwtUtil.
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    /* ---------- Config keys ---------- */
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${app.jwt.encryption.enabled:true}")
    private boolean encryptionEnabled;

    @Value("${app.jwt.encryption.key:}")
    private String encryptionKeyBase64;

    /* ---------- Runtime keys ---------- */
    private SecretKey signingKey;
    private SecretKey encryptionKey;

    /* ---------- Token constants ---------- */
    private static final String CLAIM_USERNAME      = "username";
    private static final String CLAIM_EMAIL         = "email";
    private static final String CLAIM_PUBLIC_USER_ID = "publicUserId";
    private static final String CLAIM_ROLE          = "role";

    /* ---------- Lifecycle ---------- */
    @PostConstruct
    public void init() {
        validateJwtSecret();
        this.signingKey = JwtUtil.hs512KeyFrom(jwtSecret);

        if (encryptionEnabled) {
            byte[] aesBytes = Base64.getDecoder().decode(encryptionKeyBase64.trim());
            this.encryptionKey = JwtUtil.aes256KeyFrom(aesBytes);
            logger.info("JWT provider started with AES-256-GCM encryption");
        } else {
            logger.warn("JWT provider started WITHOUT encryption");
        }
    }

    /* ---------- Token generation ---------- */
    public String createToken(User user) {
        validateUser(user);

        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + jwtExpirationMs);

        Map<String, Object> claims = Map.of(
                CLAIM_USERNAME, user.getUsername(),
                CLAIM_EMAIL, user.getEmail(),
                CLAIM_PUBLIC_USER_ID, user.getPublicUserId(),
                CLAIM_ROLE, user.getRole().name()
        );

        String jwt = JwtUtil.buildToken(claims, user.getUsername(), issuedAt, expiration, signingKey);
        return encryptionEnabled ? JwtUtil.encrypt(jwt, encryptionKey) : jwt;
    }

    /* ---------- Token parsing ---------- */
    public Claims readToken(String token) {
        String jwt = encryptionEnabled ? JwtUtil.decrypt(token, encryptionKey) : token;
        try {
            return JwtUtil.parseToken(jwt, signingKey);
        } catch (ExpiredJwtException e) {
            throw new JwtException("Token expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    /* ---------- Convenience extractors ---------- */
    public String extractUsername(String token) { return readToken(token).getSubject(); }
    public String extractEmail(String token) { return readToken(token).get(CLAIM_EMAIL, String.class); }
    public String extractPublicUserId(String token) { return readToken(token).get(CLAIM_PUBLIC_USER_ID, String.class); }
    public String extractRole(String token) { return readToken(token).get(CLAIM_ROLE, String.class); }
    public UserInfo extractUserInfo(String token) {
        Claims c = readToken(token);
        return new UserInfo(
                c.get(CLAIM_USERNAME, String.class),
                c.get(CLAIM_EMAIL, String.class),
                c.get(CLAIM_PUBLIC_USER_ID, String.class),
                c.get(CLAIM_ROLE, String.class),
                c.getIssuedAt(),
                c.getExpiration());
    }

    /* ---------- Validation ---------- */
    public boolean isValidToken(String token) {
        try {
            readToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Token invalid: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return readToken(token).getExpiration().before(new Date());
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtExpirationMs / 1000;
    }

    public long getRemainingValidityMs(String token) {
        return Math.max(0, readToken(token).getExpiration().getTime() - System.currentTimeMillis());
    }

    /* ---------- Helpers ---------- */
    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty())
            throw new IllegalStateException("JWT secret not configured");
        if (jwtSecret.length() < 64)
            throw new IllegalStateException("JWT secret must be ≥ 64 chars");
        String lower = jwtSecret.toLowerCase();
        for (String ph : new String[]{"secret", "change-me", "placeholder"})
            if (lower.contains(ph))
                throw new IllegalStateException("JWT secret contains insecure placeholder");
    }

    private void validateUser(User u) {
        if (u == null || u.getUsername() == null || u.getEmail() == null ||
                u.getPublicUserId() == null || u.getRole() == null)
            throw new IllegalArgumentException("User object incomplete");
    }

    /* ---------- DTO ---------- */
    public record UserInfo(String username,
                           String email,
                           String publicUserId,
                           String role,
                           Date issuedAt,
                           Date expiration) {}
}