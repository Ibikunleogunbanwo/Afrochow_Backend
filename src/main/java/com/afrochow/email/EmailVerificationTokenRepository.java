package com.afrochow.email;

import com.afrochow.security.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * Find a valid (non-expired, unused) token
     */
    @Query("SELECT t FROM EmailVerificationToken t WHERE " +
           "t.token = :token AND " +
           "t.isUsed = false AND " +
           "t.expiresAt > :now")
    Optional<EmailVerificationToken> findValidToken(@Param("token") String token, @Param("now") Instant now);

    /**
     * Find token by token string
     */
    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Revoke all tokens for a user
     */
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.isUsed = true WHERE t.user.userId = :userId AND t.isUsed = false")
    void revokeAllUserTokens(@Param("userId") Long userId);

    /**
     * Delete expired tokens (for cleanup jobs)
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") Instant now);
}
