package com.afrochow.security.repository;

import com.afrochow.security.model.RefreshToken;
import com.afrochow.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for RefreshToken entity
 *
 * Security Features:
 * - Token lookup by value
 * - Revocation support
 * - Cleanup of expired tokens
 * - User-based token management
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find refresh token by token value
     *
     * @param token the refresh token value
     * @return Optional containing the RefreshToken if found
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Find all refresh tokens for a user (for logout all devices)
     *
     * @param user the user
     * @return List of refresh tokens
     */
    List<RefreshToken> findByUser(User user);

    /**
     * Find all valid (non-revoked, non-expired) tokens for a user
     *
     * @param user the user
     * @param now current timestamp
     * @return List of valid refresh tokens
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false AND rt.expiryDate > :now")
    List<RefreshToken> findValidTokensByUser(@Param("user") User user, @Param("now") Instant now);

    /**
     * Find all tokens in a token family (for rotation attack detection)
     *
     * @param tokenFamily the token family ID
     * @return List of tokens in the same family
     */
    List<RefreshToken> findByTokenFamily(String tokenFamily);

    /**
     * Revoke all tokens for a user (logout from all devices)
     *
     * @param user the user
     * @return number of tokens revoked
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = CURRENT_TIMESTAMP WHERE rt.user = :user AND rt.revoked = false")
    int revokeAllByUser(@Param("user") User user);

    /**
     * Revoke all tokens in a token family (rotation attack detected)
     *
     * @param tokenFamily the token family ID
     * @return number of tokens revoked
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = CURRENT_TIMESTAMP WHERE rt.tokenFamily = :tokenFamily AND rt.revoked = false")
    int revokeAllByTokenFamily(@Param("tokenFamily") String tokenFamily);

    /**
     * Delete expired tokens (cleanup job)
     *
     * @param now current timestamp
     * @return number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    int deleteExpiredTokens(@Param("now") Instant now);

    /**
     * Delete revoked tokens older than specified date (cleanup job)
     *
     * @param cutoffDate cutoff date
     * @return number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true AND rt.revokedAt < :cutoffDate")
    int deleteRevokedTokensOlderThan(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Count active (valid) tokens for a user
     *
     * @param user the user
     * @param now current timestamp
     * @return count of active tokens
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false AND rt.expiryDate > :now")
    long countActiveTokensByUser(@Param("user") User user, @Param("now") Instant now);
}
