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
     * Find a valid (non-expired, unused) token, scoped to a single user by email.
     *
     * Deliberately NOT a global lookup by token alone: a 6-digit code only has
     * 1,000,000 possible values, and at any given time there can be many users
     * with an outstanding unused/unexpired code. A global lookup means any code
     * that happens to match ANY pending user's code succeeds — an attacker
     * doesn't even need to target a specific victim, just brute-force digits
     * until one hits. Scoping to the email the caller claims to own closes that
     * off; brute-forcing then requires knowing the victim's email AND running
     * up to 1,000,000 guesses against their account specifically, which the
     * rate limiter in AuthenticationService.verifyEmail() blocks well before
     * that's feasible.
     */
    @Query("SELECT t FROM EmailVerificationToken t WHERE " +
           "t.token = :token AND " +
           "t.user.email = :email AND " +
           "t.isUsed = false AND " +
           "t.expiresAt > :now")
    Optional<EmailVerificationToken> findValidToken(@Param("token") String token,
                                                      @Param("email") String email,
                                                      @Param("now") Instant now);

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
