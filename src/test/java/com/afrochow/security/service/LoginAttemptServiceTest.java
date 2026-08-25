package com.afrochow.security.service;

import com.afrochow.security.model.LoginAttempt;
import com.afrochow.security.repository.LoginAttemptRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginAttemptService is the OWASP-anti-automation brute-force guard behind
 * AuthenticationService.login() — these tests exercise the counting/locking state
 * machine directly (real LoginAttempt entity, mocked repository) rather than via
 * a full login() call, since AuthenticationServiceTest already covers that
 * integration at the boundary.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private SecurityEventService securityEventService;

    private LoginAttemptService loginAttemptService;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(loginAttemptRepository, securityEventService);
        ReflectionTestUtils.setField(loginAttemptService, "maxAttempts", 5);
        ReflectionTestUtils.setField(loginAttemptService, "lockoutDurationMinutes", 15L);

        httpRequest = mock(HttpServletRequest.class);
    }

    // ========== loginSucceeded ==========

    @Test
    void loginSucceeded_existingRecord_resetsCounterAndClearsLockout() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").attemptCount(3)
                .lockoutUntil(null).lastAttemptTime(Instant.now()).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        loginAttemptService.loginSucceeded("customer@example.com", httpRequest);

        assertThat(attempt.getAttemptCount()).isZero();
        verify(loginAttemptRepository).save(attempt);
        verify(securityEventService).logLoginSuccess("customer@example.com", httpRequest);
    }

    @Test
    void loginSucceeded_noExistingRecord_stillLogsSuccessWithoutSaving() {
        when(loginAttemptRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        loginAttemptService.loginSucceeded("nobody@example.com", httpRequest);

        verify(loginAttemptRepository, never()).save(any());
        verify(securityEventService).logLoginSuccess("nobody@example.com", httpRequest);
    }

    // ========== loginFailed ==========

    @Test
    void loginFailed_belowThreshold_incrementsWithoutLocking() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").attemptCount(2)
                .lastAttemptTime(Instant.now()).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        loginAttemptService.loginFailed("customer@example.com", httpRequest);

        assertThat(attempt.getAttemptCount()).isEqualTo(3);
        assertThat(attempt.isLocked()).isFalse();
        verify(loginAttemptRepository).save(attempt);
        verify(securityEventService).logFailedLoginAttempt("customer@example.com", "unknown", 3, httpRequest);
    }

    @Test
    void loginFailed_reachesMaxAttempts_locksAccount() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").attemptCount(4) // one more failure hits maxAttempts=5
                .lastAttemptTime(Instant.now()).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        loginAttemptService.loginFailed("customer@example.com", httpRequest);

        assertThat(attempt.getAttemptCount()).isEqualTo(5);
        assertThat(attempt.isLocked()).isTrue();
        assertThat(attempt.getRemainingLockoutSeconds()).isGreaterThan(0);
    }

    @Test
    void loginFailed_noExistingRecord_createsNewRecordStartingAtOne() {
        when(loginAttemptRepository.findByEmail("newattacker@example.com")).thenReturn(Optional.empty());
        ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);

        loginAttemptService.loginFailed("newattacker@example.com", httpRequest);

        verify(loginAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getEmail()).isEqualTo("newattacker@example.com");
    }

    @Test
    void loginFailed_recordsIpAddressAndUserAgent() {
        // Once ANY getHeader(...) argument is stubbed, Mockito's strict stubbing
        // requires every other getHeader(...) call on this mock to also match a
        // stub — SecurityUtils.getClientIP() checks X-Forwarded-For then X-Real-IP
        // before falling back to getRemoteAddr(), so both headers need a (null) stub.
        lenient().when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        lenient().when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.7");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.empty());
        ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);

        loginAttemptService.loginFailed("customer@example.com", httpRequest);

        verify(loginAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(captor.getValue().getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    // ========== isAccountLocked ==========

    @Test
    void isAccountLocked_activeLockout_returnsTrue() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").lockoutUntil(Instant.now().plusSeconds(300))
                .adminOverride(false).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        assertThat(loginAttemptService.isAccountLocked("customer@example.com")).isTrue();
    }

    @Test
    void isAccountLocked_expiredLockout_returnsFalse() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").lockoutUntil(Instant.now().minusSeconds(60))
                .adminOverride(false).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        assertThat(loginAttemptService.isAccountLocked("customer@example.com")).isFalse();
    }

    @Test
    void isAccountLocked_noRecord_returnsFalse() {
        when(loginAttemptRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertThat(loginAttemptService.isAccountLocked("nobody@example.com")).isFalse();
    }

    @Test
    void isAccountLocked_adminOverrideSet_returnsFalseDespiteFutureLockout() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").lockoutUntil(Instant.now().plusSeconds(300))
                .adminOverride(true).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        assertThat(loginAttemptService.isAccountLocked("customer@example.com")).isFalse();
    }

    // ========== getRemainingLockoutSeconds ==========

    @Test
    void getRemainingLockoutSeconds_activeLockout_returnsPositiveValue() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").lockoutUntil(Instant.now().plusSeconds(300))
                .adminOverride(false).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        assertThat(loginAttemptService.getRemainingLockoutSeconds("customer@example.com")).isGreaterThan(0);
    }

    @Test
    void getRemainingLockoutSeconds_noRecord_returnsZero() {
        when(loginAttemptRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertThat(loginAttemptService.getRemainingLockoutSeconds("nobody@example.com")).isZero();
    }

    // ========== getAttemptCount ==========

    @Test
    void getAttemptCount_existingRecord_returnsStoredCount() {
        LoginAttempt attempt = LoginAttempt.builder().email("customer@example.com").attemptCount(4).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        assertThat(loginAttemptService.getAttemptCount("customer@example.com")).isEqualTo(4);
    }

    @Test
    void getAttemptCount_noRecord_returnsZero() {
        when(loginAttemptRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertThat(loginAttemptService.getAttemptCount("nobody@example.com")).isZero();
    }

    // ========== unlockAccount ==========

    @Test
    void unlockAccount_existingRecord_resetsAndReturnsTrue() {
        LoginAttempt attempt = LoginAttempt.builder()
                .email("customer@example.com").attemptCount(5)
                .lockoutUntil(Instant.now().plusSeconds(300)).build();
        when(loginAttemptRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(attempt));

        boolean result = loginAttemptService.unlockAccount("customer@example.com");

        assertThat(result).isTrue();
        assertThat(attempt.getAttemptCount()).isZero();
        assertThat(attempt.isLocked()).isFalse();
        verify(loginAttemptRepository).save(attempt);
    }

    @Test
    void unlockAccount_noRecord_returnsFalse() {
        when(loginAttemptRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        boolean result = loginAttemptService.unlockAccount("nobody@example.com");

        assertThat(result).isFalse();
        verify(loginAttemptRepository, never()).save(any());
    }
}
