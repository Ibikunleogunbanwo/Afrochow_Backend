package com.afrochow.security.repository;

import com.afrochow.security.model.PasswordResetToken;
import com.afrochow.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Find all tokens for a user (used to validate or revoke)
     */
    List<PasswordResetToken> findByUser(User user);

    /**
     * Delete all expired or used tokens (cleanup)
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiryDate < :now OR prt.used = true")
    int deleteExpiredOrUsedTokens(@Param("now") Instant now);

    /**
     * Revoke (mark as used) all tokens for a user
     */
    @Modifying
    @Query("UPDATE PasswordResetToken prt SET prt.used = true WHERE prt.user.userId = :userId AND prt.used = false")
    void revokeAllUserTokens(@Param("userId") Long userId);

    /**
     * Count active (not used, not expired) tokens for a user
     */
    @Query("SELECT COUNT(prt) FROM PasswordResetToken prt WHERE prt.user.userId = :userId AND prt.used = false AND prt.expiryDate > :now")
    long countActiveTokensByUser(@Param("userId") Long userId, @Param("now") Instant now);
}
