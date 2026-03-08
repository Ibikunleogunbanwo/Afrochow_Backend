package com.afrochow.security.Services;

import com.afrochow.common.exceptions.TokenRefreshException;
import com.afrochow.security.dto.ActiveSessionDto;
import com.afrochow.security.model.RefreshToken;
import com.afrochow.user.model.User;
import com.afrochow.security.repository.RefreshTokenRepository;
import com.afrochow.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing refresh tokens with enhanced security features:
 * - HMAC-SHA256 token hashing with secret key
 * - Token rotation to prevent replay attacks
 * - Token family tracking for security monitoring
 * - Session limits per user
 * - IP and User-Agent tracking
 * - Token length validation to prevent DoS
 */
@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int MAX_ACTIVE_TOKENS_PER_USER = 5;
    private static final String UNKNOWN = "unknown";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // Token validation constants
    private static final int MIN_TOKEN_LENGTH = 32;
    private static final int MAX_TOKEN_LENGTH = 256;
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Mac hmacSha256;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private Long refreshTokenDurationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            @Value("${app.security.token-secret}") String tokenSecret
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.hmacSha256 = initializeHmac(tokenSecret);
    }

    /**
     * Initialize HMAC-SHA256 with secret key.
     * This method runs once during service initialization.
     */
    private Mac initializeHmac(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("Token secret key must be configured in application properties");
        }

        if (secret.length() < 32) {
            logger.warn("Token secret key is too short. Recommended minimum: 32 characters");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            logger.info("HMAC-SHA256 initialized successfully for token hashing");
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Failed to initialize HMAC-SHA256", e);
            throw new IllegalStateException("Failed to initialize token hashing", e);
        }
    }

    /**
     * Hash token using HMAC-SHA256 with secret key.
     * Thread-safe implementation using synchronized block.
     *
     * @param rawToken Raw token string
     * @return Base64-encoded HMAC hash
     */
    private String hashToken(String rawToken) {
        validateTokenFormat(rawToken);

        try {
            byte[] hash;
            // Synchronized to ensure thread safety with shared Mac instance
            synchronized (hmacSha256) {
                hash = hmacSha256.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("Failed to hash token", e);
            throw new TokenRefreshException("Token processing failed");
        }
    }

    /**
     * Validate token format to prevent DoS and injection attacks.
     *
     * @param token Token to validate
     * @throws TokenRefreshException if token is invalid
     */
    private void validateTokenFormat(String token) {
        if (token == null) {
            throw new TokenRefreshException("Token cannot be null");
        }

        String trimmedToken = token.trim();

        if (trimmedToken.isEmpty()) {
            throw new TokenRefreshException("Token cannot be empty");
        }

        // Length validation to prevent DoS
        if (trimmedToken.length() < MIN_TOKEN_LENGTH) {
            throw new TokenRefreshException("Token is too short");
        }

        if (trimmedToken.length() > MAX_TOKEN_LENGTH) {
            logger.warn("Rejecting excessively long token (length: {})", trimmedToken.length());
            throw new TokenRefreshException("Token exceeds maximum allowed length");
        }

        // Validate UUID format (our tokens are UUIDs)
        if (!trimmedToken.matches(UUID_PATTERN)) {
            logger.warn("Invalid token format detected");
            throw new TokenRefreshException("Invalid token format");
        }
    }

    /**
     * Create a new refresh token for a user identified by username.
     *
     * @param username Username of the user
     * @param request HTTP request for IP and User-Agent extraction
     * @return Raw token string (not hashed) to return to client
     * @throws UsernameNotFoundException if user not found
     */
    @Transactional
    public String createRefreshToken(String username, HttpServletRequest request) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return createRefreshTokenForUser(user, request);
    }

    /**
     * Create a new refresh token for a user entity.
     *
     * @param user User entity
     * @param request HTTP request for IP and User-Agent extraction
     * @return Raw token string (not hashed) to return to client
     */
    @Transactional
    public String createRefreshTokenForUser(User user, HttpServletRequest request) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        // Cleanup old tokens if user has too many active sessions
        cleanupExcessiveTokens(user);

        String tokenFamily = UUID.randomUUID().toString();
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(hashedToken)
                .tokenFamily(tokenFamily)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .revoked(false)
                .ipAddress(extractIpAddress(request))
                .userAgent(extractUserAgent(request))
                .build();

        refreshTokenRepository.save(refreshToken);

        logger.info("Created refresh token for user: {} (ID: {}) from IP: {}",
                user.getUsername(), user.getPublicUserId(), refreshToken.getIpAddress());

        // Return raw token to client (never store or log this)
        return rawToken;
    }

    /**
     * Verify and return the refresh token entity if valid.
     * This is an internal method - it returns the entity with hashed token.
     *
     * @param rawToken Raw token string from client
     * @return RefreshToken entity if valid
     * @throws TokenRefreshException if token is invalid, expired, or revoked
     */
    @Transactional(readOnly = true)
    public RefreshToken verifyRefreshToken(String rawToken) {
        validateTokenFormat(rawToken);
        String hashedToken = hashToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        // Check if token is revoked
        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            logger.warn("Attempted use of revoked refresh token for user: {} (ID: {})",
                    refreshToken.getUser().getUsername(),
                    refreshToken.getUser().getPublicUserId());
            throw new TokenRefreshException("Refresh token was revoked");
        }

        // Check if the token is expired
        if (refreshToken.isExpired()) {
            logger.warn("Attempted use of expired refresh token for user: {} (ID: {})",
                    refreshToken.getUser().getUsername(),
                    refreshToken.getUser().getPublicUserId());
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException("Refresh token has expired. Please login again");
        }

        return refreshToken;
    }

    /**
     * Rotate refresh token - revoke old token and create new one in same family.
     * Implements token rotation to prevent replay attacks.
     *
     * @param rawToken The raw refresh token string from client
     * @param request HTTP request for IP and User-Agent
     * @return New raw refresh token string
     * @throws TokenRefreshException if rotation fails or token reuse detected
     */
    @Transactional
    public String rotateRefreshToken(String rawToken, HttpServletRequest request) {
        validateTokenFormat(rawToken);
        String hashedToken = hashToken(rawToken);

        // Find the token in database
        RefreshToken oldToken = refreshTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new TokenRefreshException("Refresh token not found"));

        // Check if already revoked (possible token reuse attack)
        if (Boolean.TRUE.equals(oldToken.getRevoked())) {
            logger.error("SECURITY ALERT: Token reuse detected for user: {} (ID: {}) in family: {}",
                    oldToken.getUser().getUsername(),
                    oldToken.getUser().getPublicUserId(),
                    oldToken.getTokenFamily());
            revokeTokenFamily(oldToken.getTokenFamily());
            throw new TokenRefreshException("Token reuse detected. All tokens in family revoked for security.");
        }

        // Check if expired
        if (oldToken.isExpired()) {
            logger.warn("Attempted rotation of expired token for user: {} (ID: {})",
                    oldToken.getUser().getUsername(),
                    oldToken.getUser().getPublicUserId());
            refreshTokenRepository.delete(oldToken);
            throw new TokenRefreshException("Refresh token has expired. Please login again");
        }

        try {
            // Revoke the old token
            oldToken.revoke();
            refreshTokenRepository.save(oldToken);

            // Create new token in the same family
            String newRawToken = UUID.randomUUID().toString();
            String newHashedToken = hashToken(newRawToken);

            RefreshToken newToken = RefreshToken.builder()
                    .user(oldToken.getUser())
                    .token(newHashedToken) // Store HMAC hash
                    .tokenFamily(oldToken.getTokenFamily()) // Same family for rotation tracking
                    .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                    .revoked(false)
                    .ipAddress(extractIpAddress(request))
                    .userAgent(extractUserAgent(request))
                    .build();

            refreshTokenRepository.save(newToken);

            logger.info("Rotated refresh token for user: {} (ID: {}) from IP: {}",
                    oldToken.getUser().getUsername(),
                    oldToken.getUser().getPublicUserId(),
                    newToken.getIpAddress());

            // Return new raw token to client
            return newRawToken;

        } catch (Exception e) {
            logger.error("Failed to rotate refresh token for user: {} (ID: {})",
                    oldToken.getUser().getUsername(),
                    oldToken.getUser().getPublicUserId(), e);
            throw new TokenRefreshException("Token rotation failed: " + e.getMessage());
        }
    }

    /**
     * Revoke a specific refresh token.
     *
     * @param rawToken Raw token string from client
     */
    @Transactional
    public void revokeToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            logger.warn("Attempted to revoke null or empty token");
            return;
        }

        try {
            validateTokenFormat(rawToken);
            String hashedToken = hashToken(rawToken);

            refreshTokenRepository.findByToken(hashedToken).ifPresent(rt -> {
                rt.revoke();
                refreshTokenRepository.save(rt);
                logger.info("Revoked refresh token for user: {} (ID: {})",
                        rt.getUser().getUsername(), rt.getUser().getPublicUserId());
            });
        } catch (TokenRefreshException e) {
            logger.warn("Invalid token format during revocation: {}", e.getMessage());
        }
    }

    /**
     * Revoke all tokens for a user (logout from all devices).
     *
     * @param username Username of the user
     * @throws UsernameNotFoundException if user not found
     */
    @Transactional
    public void revokeAllUserTokens(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        int revokedCount = refreshTokenRepository.revokeAllByUser(user);
        logger.info("Revoked {} refresh tokens for user: {} (ID: {})",
                revokedCount, user.getUsername(), user.getPublicUserId());
    }

    /**
     * Revoke all tokens for a user by User entity.
     *
     * @param user User entity
     * @return Number of tokens revoked
     */
    @Transactional
    public int revokeAllUserTokens(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        int revokedCount = refreshTokenRepository.revokeAllByUser(user);
        logger.info("Revoked {} refresh tokens for user: {} (ID: {})",
                revokedCount, user.getUsername(), user.getPublicUserId());

        return revokedCount;
    }

    /**
     * Revoke all tokens in a token family (security incident response).
     * Called when token reuse is detected.
     *
     * @param tokenFamily Token family UUID
     * @return Number of tokens revoked
     */
    @Transactional
    public int revokeTokenFamily(String tokenFamily) {
        if (tokenFamily == null || tokenFamily.trim().isEmpty()) {
            logger.warn("Attempted to revoke null or empty token family");
            return 0;
        }

        int revokedCount = refreshTokenRepository.revokeAllByTokenFamily(tokenFamily);
        logger.warn("SECURITY: Revoked {} tokens in family {} due to suspected attack",
                revokedCount, tokenFamily);

        return revokedCount;
    }

    /**
     * Delete expired refresh tokens (cleanup job).
     * Should be run periodically via @Scheduled task.
     *
     * @return Number of tokens deleted
     */
    @Transactional
    public int deleteExpiredTokens() {
        int deletedCount = refreshTokenRepository.deleteExpiredTokens(Instant.now());
        if (deletedCount > 0) {
            logger.info("Cleaned up {} expired refresh tokens", deletedCount);
        }
        return deletedCount;
    }

    /**
     * Get all active tokens for a user (for security dashboard).
     *
     * @param username Username
     * @return List of active refresh tokens
     */
    @Transactional(readOnly = true)
    public List<RefreshToken> getActiveUserTokens(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return refreshTokenRepository.findValidTokensByUser(user, Instant.now());
    }

    /**
     * Cleanup excessive tokens for a user (keep only recent N tokens).
     * Prevents users from accumulating unlimited tokens.
     *
     * @param user User entity
     */
    private void cleanupExcessiveTokens(User user) {
        long activeTokenCount = refreshTokenRepository.countActiveTokensByUser(user, Instant.now());

        if (activeTokenCount >= MAX_ACTIVE_TOKENS_PER_USER) {
            logger.info("User {} has {} active tokens, revoking oldest tokens",
                    user.getUsername(), activeTokenCount);

            List<RefreshToken> validTokens = refreshTokenRepository
                    .findValidTokensByUser(user, Instant.now());

            // Sort by creation date and revoke the oldest tokens
            int tokensToRevoke = (int) (activeTokenCount - MAX_ACTIVE_TOKENS_PER_USER + 1);

            validTokens.stream()
                    .sorted(Comparator.comparing(RefreshToken::getCreatedAt))
                    .limit(tokensToRevoke)
                    .forEach(token -> {
                        token.revoke();
                        refreshTokenRepository.save(token);
                    });

            logger.info("Revoked {} oldest tokens for user: {}", tokensToRevoke, user.getUsername());
        }
    }

    /**
     * Extract IP address from HTTP request.
     * Handles X-Forwarded-For header for proxied requests.
     */
    private String extractIpAddress(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        // If X-Forwarded-For contains multiple IPs, take the first one (original client)
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress != null ? ipAddress : UNKNOWN;
    }

    /**
     * Extract User-Agent from HTTP request.
     */
    private String extractUserAgent(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String userAgent = request.getHeader("User-Agent");

        // Limit length to prevent database issues with extremely long User-Agent strings
        if (userAgent != null && userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }

        return userAgent != null ? userAgent : UNKNOWN;
    }

    // ==================== SESSION MANAGEMENT METHODS ====================

    /**
     * Get all active sessions for a user
     *
     * @param username Username of the user
     * @return List of active session DTOs
     */
    @Transactional(readOnly = true)
    public List<ActiveSessionDto> getActiveSessions(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<RefreshToken> activeTokens = refreshTokenRepository.findValidTokensByUser(user, Instant.now());

        return activeTokens.stream()
                .map(this::convertToActiveSessionDto)
                .collect(Collectors.toList());
    }

    /**
     * Revoke a specific session for a user
     *
     * @param username Username of the user
     * @param tokenId ID of the token to revoke
     */
    @Transactional
    public void revokeSession(String username, Long tokenId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        RefreshToken token = refreshTokenRepository.findById(tokenId)
                .orElseThrow(() -> new TokenRefreshException("Session not found"));

        // Verify that the token belongs to the user
        if (!token.getUser().getUserId().equals(user.getUserId())) {
            throw new TokenRefreshException("Session does not belong to the user");
        }

        // Revoke the token
        token.revoke();
        refreshTokenRepository.save(token);

        logger.info("Session {} revoked for user: {}", tokenId, username);
    }

    /**
     * Convert RefreshToken to ActiveSessionDto
     */
    private ActiveSessionDto convertToActiveSessionDto(RefreshToken token) {
        return ActiveSessionDto.builder()
                .tokenId(token.getId())
                .deviceInfo(token.getUserAgent() != null ? token.getUserAgent() : "Unknown Device")
                .ipAddress(token.getIpAddress())
                .lastUsedAt(token.getCreatedAt())
                .createdAt(token.getCreatedAt())
                .isCurrentSession(false) // Will be set by controller if needed
                .build();
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenDurationMs / 1000;
    }
}