package com.afrochow.security.Services;

import com.afrochow.security.model.LoginAttempt;
import com.afrochow.security.repository.LoginAttemptRepository;
import com.afrochow.security.Utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**

 * Service for managing login attempts and account lockout
 *
 * SECURITY: Implements OWASP ASVS V2.2.1 (Anti-Automation)
 * * Protects against brute force attacks
 * * Enforces temporary account lockout
 * * Tracks attempts by email and IP address
 * * Automatic cleanup of old records
 */
@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private final LoginAttemptRepository loginAttemptRepository;
    private final SecurityEventService securityEventService;

    @Value("${security.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.login.lockout-duration-minutes:15}")
    private long lockoutDurationMinutes;

    @Value("${security.login.cleanup-days:30}")
    private int cleanupDays;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository,
                               SecurityEventService securityEventService) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.securityEventService = securityEventService;
    }

    /**

     * Record a successful login
     *
     * SECURITY: Resets attempt counter, unlocks account, log event
     */
    @Transactional
    public void loginSucceeded(String email, HttpServletRequest request) {
        loginAttemptRepository.findByEmail(email).ifPresent(attempt -> {
            if (attempt.getAttemptCount() > 0) {
                logger.info("Login succeeded for {} after {} failed attempts", email, attempt.getAttemptCount());
            }
            attempt.resetAttempts();
            loginAttemptRepository.save(attempt);
        });

        // Log success in security events
        securityEventService.logLoginSuccess(email, request);
    }

    /**

     * Record a failed login attempt
     *
     * SECURITY:
     * * Increments attempt a counter
     * * Locks account if a threshold exceeded
     * * Logs security events
     * * Tracks IP address and User-Agent
     */
    @Transactional
    public void loginFailed(String email, HttpServletRequest request) {
        String ipAddress = SecurityUtils.getClientIP(request);
        String userAgent = request.getHeader("User-Agent");

        LoginAttempt attempt = loginAttemptRepository.findByEmail(email)
                .orElse(LoginAttempt.builder()
                        .email(email)
                        .attemptCount(0)
                        .lastAttemptTime(Instant.now())
                        .build());

        attempt.incrementAttempts();
        attempt.setIpAddress(ipAddress);
        attempt.setUserAgent(userAgent);

        // Lock account if a threshold exceeded
        if (attempt.getAttemptCount() >= maxAttempts) {
            long lockoutSeconds = lockoutDurationMinutes * 60;
            attempt.lockAccount(lockoutSeconds);


            logger.warn("SECURITY ALERT: Account {} locked after {} failed attempts from IP {}",
                    email, attempt.getAttemptCount(), ipAddress);

        } else {
            logger.info("Failed login attempt {} of {} for {} from IP {}",
                    attempt.getAttemptCount(), maxAttempts, email, ipAddress);
        }

        loginAttemptRepository.save(attempt);

        // Log failed login in security events
        securityEventService.logFailedLoginAttempt(email, ipAddress, attempt.getAttemptCount(), request);
    }

    /**

     * Check if an account is currently locked
     */
    @Transactional(readOnly = true)
    public boolean isAccountLocked(String email) {
        return loginAttemptRepository.findByEmail(email)
                .map(LoginAttempt::isLocked)
                .orElse(false);
    }

    /**

     * Get remaining lockout time for an account
     */
    @Transactional(readOnly = true)
    public long getRemainingLockoutSeconds(String email) {
        return loginAttemptRepository.findByEmail(email)
                .map(LoginAttempt::getRemainingLockoutSeconds)
                .orElse(0L);
    }

    /**

     * Get current attempt count for an account
     */
    @Transactional(readOnly = true)
    public int getAttemptCount(String email) {
        return loginAttemptRepository.findByEmail(email)
                .map(LoginAttempt::getAttemptCount)
                .orElse(0);
    }

    /**

     * Manually unlock an account (admin operation)
     */
    @Transactional
    public boolean unlockAccount(String email) {
        return loginAttemptRepository.findByEmail(email)
                .map(attempt -> {
                    attempt.resetAttempts();
                    attempt.setAdminOverride(true);
                    loginAttemptRepository.save(attempt);
                    logger.info("SECURITY: Account {} manually unlocked by admin", email);
                    return true;
                })
                .orElse(false);
    }

    /**

     * Get all currently locked accounts
     */
    @Transactional(readOnly = true)
    public List<LoginAttempt> getCurrentlyLockedAccounts() {
        return loginAttemptRepository.findCurrentlyLocked(Instant.now());
    }

    /**

     * Get login attempts within a time range
     */
    @Transactional(readOnly = true)
    public List<LoginAttempt> getLoginAttemptsByTimeRange(Instant startTime, Instant endTime) {
        return loginAttemptRepository.findByTimeRange(startTime, endTime);
    }

    /**

     * Automatic cleanup of old login attempt records
     */
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3:00 AM
    @Transactional
    public void cleanupOldAttempts() {
        Instant cutoffDate = Instant.now().minus(cleanupDays, ChronoUnit.DAYS);
        Instant now = Instant.now();

        int deletedCount = loginAttemptRepository.deleteOldAttempts(cutoffDate, now);

        if (deletedCount > 0) {
            logger.info("Cleaned up {} old login attempt records older than {} days",
                    deletedCount, cleanupDays);
        }
    }

    /**

     * Get security statistics
     */
    @Transactional(readOnly = true)
    public LoginAttemptStatistics getStatistics() {
        long currentlyLocked = loginAttemptRepository.countCurrentlyLocked(Instant.now());
        List<LoginAttempt> highAttempts = loginAttemptRepository.findHighAttemptCount(3, Instant.now());

        return new LoginAttemptStatistics(
                currentlyLocked,
                highAttempts.size(),
                maxAttempts,
                lockoutDurationMinutes
        );
    }

    /**

     * Statistics about login attempts
     */
    public record LoginAttemptStatistics(
            long currentlyLockedCount,
            int highAttemptCount,
            int maxAttemptsThreshold,
            long lockoutDurationMinutes
    ) {}
}
