package com.afrochow.security.repository;

import com.afrochow.security.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for LoginAttempt entity
 *
 * SECURITY: All queries use parameterized JPQL to prevent SQL injection
 */
@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /**
     * Find login attempt record by email address
     *
     * SECURITY: Uses indexed email column for fast lookups
     *
     * @param email User's email address
     * @return LoginAttempt if exists, empty Optional otherwise
     */
    Optional<LoginAttempt> findByEmail(String email);

    /**
     * Find all login attempts from a specific IP address
     *
     * SECURITY: Used to detect distributed brute force attacks
     *
     * @param ipAddress IP address to search for
     * @return List of login attempts from this IP
     */
    List<LoginAttempt> findByIpAddress(String ipAddress);

    /**
     * Find currently locked accounts
     *
     * SECURITY: Returns accounts where lockout hasn't expired yet
     *
     * @param now Current timestamp
     * @return List of locked login attempts
     */
    @Query("SELECT la FROM LoginAttempt la WHERE la.lockoutUntil > :now AND la.adminOverride = false")
    List<LoginAttempt> findCurrentlyLocked(@Param("now") Instant now);

    /**
     * Find accounts with high attempt counts but not yet locked
     *
     * SECURITY: Used for monitoring potential attacks
     *
     * @param threshold Minimum attempt count
     * @return List of login attempts exceeding threshold
     */
    @Query("SELECT la FROM LoginAttempt la WHERE la.attemptCount >= :threshold AND (la.lockoutUntil IS NULL OR la.lockoutUntil < :now)")
    List<LoginAttempt> findHighAttemptCount(@Param("threshold") int threshold, @Param("now") Instant now);

    /**
     * Delete old login attempt records (cleanup)
     *
     * SECURITY: Prevents table from growing unbounded
     * - Removes records older than cutoff date
     * - Only removes unlocked accounts with no attempts
     *
     * @param cutoffDate Records older than this will be deleted
     * @return Number of records deleted
     */
    @Modifying
    @Query("DELETE FROM LoginAttempt la WHERE la.lastAttemptTime < :cutoffDate AND la.attemptCount = 0 AND (la.lockoutUntil IS NULL OR la.lockoutUntil < :now)")
    int deleteOldAttempts(@Param("cutoffDate") Instant cutoffDate, @Param("now") Instant now);

    /**
     * Count currently locked accounts
     *
     * SECURITY: Used for security monitoring and alerting
     *
     * @param now Current timestamp
     * @return Number of currently locked accounts
     */
    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.lockoutUntil > :now AND la.adminOverride = false")
    long countCurrentlyLocked(@Param("now") Instant now);

    /**
     * Find login attempts within a time range
     *
     * SECURITY: Used for security auditing and analysis
     *
     * @param startTime Start of time range
     * @param endTime End of time range
     * @return List of login attempts in the time range
     */
    @Query("SELECT la FROM LoginAttempt la WHERE la.lastAttemptTime BETWEEN :startTime AND :endTime ORDER BY la.lastAttemptTime DESC")
    List<LoginAttempt> findByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find accounts locked by specific IP address pattern
     *
     * SECURITY: Detect attacks from IP ranges
     *
     * @param ipPrefix IP address prefix (e.g., "192.168.")
     * @param now Current timestamp
     * @return List of locked attempts from matching IPs
     */
    @Query("SELECT la FROM LoginAttempt la WHERE la.ipAddress LIKE :ipPrefix AND la.lockoutUntil > :now")
    List<LoginAttempt> findLockedByIpPrefix(@Param("ipPrefix") String ipPrefix, @Param("now") Instant now);
}
