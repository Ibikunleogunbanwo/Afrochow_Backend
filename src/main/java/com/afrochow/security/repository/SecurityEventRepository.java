package com.afrochow.security.repository;

import com.afrochow.security.model.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for SecurityEvent entity
 *
 * SECURITY: All queries use parameterized JPQL to prevent SQL injection
 */
@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    /**
     * Find all security events for a specific user
     *
     * @param userId User ID
     * @param pageable Pagination parameters
     * @return Page of security events
     */
    Page<SecurityEvent> findByUserId(Long userId, Pageable pageable);

    /**
     * Find all security events by email address
     *
     * @param email Email address
     * @param pageable Pagination parameters
     * @return Page of security events
     */
    Page<SecurityEvent> findByEmail(String email, Pageable pageable);

    /**
     * Find security events by type
     *
     * @param eventType Type of security event
     * @param pageable Pagination parameters
     * @return Page of security events
     */
    Page<SecurityEvent> findByEventType(SecurityEvent.SecurityEventType eventType, Pageable pageable);

    /**
     * Find security events by severity level
     *
     * @param severity Severity level
     * @param pageable Pagination parameters
     * @return Page of security events
     */
    Page<SecurityEvent> findBySeverity(SecurityEvent.SecurityEventSeverity severity, Pageable pageable);

    /**
     * Find security events by IP address
     *
     * @param ipAddress IP address
     * @param pageable Pagination parameters
     * @return Page of security events
     */
    Page<SecurityEvent> findByIpAddress(String ipAddress, Pageable pageable);

    /**
     * Find security events within a time range
     *
     * @param startTime Start of time range
     * @param endTime End of time range
     * @param pageable Pagination parameters
     * @return Page of security events
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.eventTime BETWEEN :startTime AND :endTime ORDER BY se.eventTime DESC")
    Page<SecurityEvent> findByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime, Pageable pageable);

    /**
     * Find security events for a user within a time range
     *
     * @param userId User ID
     * @param startTime Start of time range
     * @param endTime End of time range
     * @return List of security events
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.userId = :userId AND se.eventTime BETWEEN :startTime AND :endTime ORDER BY se.eventTime DESC")
    List<SecurityEvent> findByUserIdAndTimeRange(@Param("userId") Long userId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find recent security events for a user
     *
     * @param userId User ID
     * @param since Time to look back from
     * @param pageable Pagination parameters
     * @return Page of recent security events
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.userId = :userId AND se.eventTime > :since ORDER BY se.eventTime DESC")
    Page<SecurityEvent> findRecentByUserId(@Param("userId") Long userId, @Param("since") Instant since, Pageable pageable);

    /**
     * Count security events by type for a user
     *
     * @param userId User ID
     * @param eventType Event type
     * @param since Time to count from
     * @return Count of events
     */
    @Query("SELECT COUNT(se) FROM SecurityEvent se WHERE se.userId = :userId AND se.eventType = :eventType AND se.eventTime > :since")
    long countByUserIdAndEventTypeSince(@Param("userId") Long userId, @Param("eventType") SecurityEvent.SecurityEventType eventType, @Param("since") Instant since);

    /**
     * Count security events by IP address
     *
     * @param ipAddress IP address
     * @param eventType Event type
     * @param since Time to count from
     * @return Count of events
     */
    @Query("SELECT COUNT(se) FROM SecurityEvent se WHERE se.ipAddress = :ipAddress AND se.eventType = :eventType AND se.eventTime > :since")
    long countByIpAddressAndEventTypeSince(@Param("ipAddress") String ipAddress, @Param("eventType") SecurityEvent.SecurityEventType eventType, @Param("since") Instant since);

    /**
     * Find high-severity events (ERROR or CRITICAL)
     *
     * @param since Time to look back from
     * @param pageable Pagination parameters
     * @return Page of high-severity events
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.severity IN ('ERROR', 'CRITICAL') AND se.eventTime > :since ORDER BY se.eventTime DESC")
    Page<SecurityEvent> findHighSeverityEventsSince(@Param("since") Instant since, Pageable pageable);

    /**
     * Find security events by email and event type within time range
     *
     * @param email Email address
     * @param eventType Event type
     * @param since Time to look back from
     * @return List of security events
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.email = :email AND se.eventType = :eventType AND se.eventTime > :since ORDER BY se.eventTime DESC")
    List<SecurityEvent> findByEmailAndEventTypeSince(@Param("email") String email, @Param("eventType") SecurityEvent.SecurityEventType eventType, @Param("since") Instant since);

    /**
     * Delete old security events (cleanup)
     *
     * SECURITY: Removes events older than retention period
     * - Keeps database clean
     * - Maintains compliance with data retention policies
     *
     * @param cutoffDate Events older than this will be deleted
     * @return Number of events deleted
     */
    @Modifying
    @Query("DELETE FROM SecurityEvent se WHERE se.eventTime < :cutoffDate AND se.severity IN ('INFO', 'WARNING')")
    int deleteOldEventsByDate(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Get security event statistics
     *
     * @param since Time to look back from
     * @return List of [event_type, count] pairs
     */
    @Query("SELECT se.eventType, COUNT(se) FROM SecurityEvent se WHERE se.eventTime > :since GROUP BY se.eventType ORDER BY COUNT(se) DESC")
    List<Object[]> getEventStatisticsSince(@Param("since") Instant since);
}
