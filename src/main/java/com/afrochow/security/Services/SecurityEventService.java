package com.afrochow.security.Services;

import com.afrochow.security.model.SecurityEvent;
import com.afrochow.security.repository.SecurityEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**

 * Unified Security Event Service
 * * Handles all security-related events
 * * Uses builder pattern for consistency
 * * Captures optional metadata and HTTP request details
 */
@Service
public class SecurityEventService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityEventService.class);

    private final SecurityEventRepository securityEventRepository;
    private final int retentionDays;

    public SecurityEventService(SecurityEventRepository securityEventRepository,
                                @org.springframework.beans.factory.annotation.Value("${security.events.retention-days:90}") int retentionDays) {
        this.securityEventRepository = securityEventRepository;
        this.retentionDays = retentionDays;
    }

    /**

     * Generic event logging
     */
    @Transactional
    public void logEvent(
            SecurityEvent.SecurityEventType eventType,
            SecurityEvent.SecurityEventSeverity severity,
            String description,
            HttpServletRequest request,
            Long userId,
            String email,
            String metadata
    ) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .email(email)
                .eventType(eventType)
                .severity(severity)
                .description(description)
                .metadata(metadata)
                .ipAddress(request != null ? getClientIP(request) : "unknown")
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .requestUri(request != null ? request.getRequestURI() : null)
                .httpMethod(request != null ? request.getMethod() : null)
                .eventTime(Instant.now())
                .build();

        securityEventRepository.save(event);

        String logMessage = String.format("SECURITY_EVENT: %s | %s | User: %s | IP: %s | %s%s",
                eventType,
                severity,
                email != null ? email : "N/A",
                event.getIpAddress(),
                description,
                metadata != null ? " | Metadata: " + metadata : "");

        switch (severity) {
            case CRITICAL, ERROR -> logger.error(logMessage);
            case WARNING -> logger.warn(logMessage);
            case INFO -> logger.info(logMessage);
        }
    }

    /**

     * Dedicated methods for common events
     */

    @Transactional
    public void logRegistration(String email, HttpServletRequest request) {
        logEvent(SecurityEvent.SecurityEventType.ADMIN_ACTION_PERFORMED, // or USER_REGISTERED
                SecurityEvent.SecurityEventSeverity.INFO,
                "New user registration",
                request,
                null,
                email,
                null);
    }

    @Transactional
    public void logLoginSuccess(String email, HttpServletRequest request) {
        logEvent(SecurityEvent.SecurityEventType.LOGIN_SUCCESS,
                SecurityEvent.SecurityEventSeverity.INFO,
                "User logged in successfully",
                request,
                null,
                email,
                null);
    }

    @Transactional
    public void logFailedLoginAttempt(String email, String clientIp, int attemptCount, HttpServletRequest request) {
        logEvent(SecurityEvent.SecurityEventType.LOGIN_FAILED,
                SecurityEvent.SecurityEventSeverity.WARNING,
                "Failed login attempt " + attemptCount,
                request,
                null,
                email,
                null);
    }

    @Transactional
    public void logPasswordResetRequest(String email, HttpServletRequest request) {
        logEvent(SecurityEvent.SecurityEventType.PASSWORD_CHANGED,
                SecurityEvent.SecurityEventSeverity.INFO,
                "Password reset requested",
                request,
                null,
                email,
                null);
    }

    @Transactional
    public void logPasswordResetCompleted(String email, HttpServletRequest request) {
        logEvent(SecurityEvent.SecurityEventType.PASSWORD_CHANGED,
                SecurityEvent.SecurityEventSeverity.INFO,
                "Password reset completed",
                request,
                null,
                email,
                null);
    }

    /**

     * Pagination queries
     */
    @Transactional(readOnly = true)
    public Page<SecurityEvent> getUserEvents(Long userId, Pageable pageable) {
        return securityEventRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SecurityEvent> getEventsByEmail(String email, Pageable pageable) {
        return securityEventRepository.findByEmail(email, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SecurityEvent> getRecentUserEvents(Long userId, int hours, Pageable pageable) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return securityEventRepository.findRecentByUserId(userId, since, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SecurityEvent> getHighSeverityEvents(int hours, Pageable pageable) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return securityEventRepository.findHighSeverityEventsSince(since, pageable);
    }

    /**

     * Count-based queries
     */
    @Transactional(readOnly = true)
    public long countFailedLoginAttempts(Long userId, int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return securityEventRepository.countByUserIdAndEventTypeSince(userId,
                SecurityEvent.SecurityEventType.LOGIN_FAILED, since);
    }

    @Transactional(readOnly = true)
    public long countEventsByIP(String ipAddress, SecurityEvent.SecurityEventType eventType, int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return securityEventRepository.countByIpAddressAndEventTypeSince(ipAddress, eventType, since);
    }

    @Transactional(readOnly = true)
    public Map<SecurityEvent.SecurityEventType, Long> getEventStatistics(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<Object[]> results = securityEventRepository.getEventStatisticsSince(since);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (SecurityEvent.SecurityEventType) row[0],
                        row -> (Long) row[1]
                ));

    }


    @Transactional
    public void logLockedAccountAttempt(String identifier, String clientIp, long remainingMinutes) {
        logEvent(
                SecurityEvent.SecurityEventType.LOGIN_LOCKED,
                SecurityEvent.SecurityEventSeverity.WARNING,
                "Login attempt on locked account. Remaining lockout: " + remainingMinutes + " minutes",
                null, // no HttpServletRequest needed if you pass clientIp
                null,
                identifier,
                null
        );
    }

    /**

     * Scheduled cleanup of old events (INFO/WARNING only)
     */
    @Scheduled(cron = "0 0 4 * * *") // Daily 4 AM
    @Transactional
    public void cleanupOldEvents() {
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedCount = securityEventRepository.deleteOldEventsByDate(cutoffDate);
        if (deletedCount > 0) {
            logger.info("Cleaned up {} old security events older than {} days", deletedCount, retentionDays);
        }
    }

    /**

     * Extract client IP
     */
    private String getClientIP(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) ipAddress = request.getHeader("X-Real-IP");
        if (ipAddress == null || ipAddress.isEmpty()) ipAddress = request.getRemoteAddr();
        if (ipAddress != null && ipAddress.contains(",")) ipAddress = ipAddress.split(",")[0].trim();
        return ipAddress != null ? ipAddress : "unknown";
    }


}
