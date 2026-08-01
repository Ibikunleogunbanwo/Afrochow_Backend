package com.afrochow.auth.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.admin.repository.AdminProfileRepository;
import com.afrochow.auth.dto.ForgotPasswordRequestDto;
import com.afrochow.auth.dto.LoginRequest;
import com.afrochow.auth.dto.LoginResponse;
import com.afrochow.auth.dto.RegistrationResponse;
import com.afrochow.auth.dto.ResetPasswordRequestDto;
import com.afrochow.common.enums.AuthProvider;
import com.afrochow.common.enums.Province;
import com.afrochow.common.enums.Role;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.common.exceptions.AccountLockedException;
import com.afrochow.common.exceptions.CustomerWaitlistModeException;
import com.afrochow.common.exceptions.EmailAlreadyExistsException;
import com.afrochow.common.exceptions.EmailNotVerifiedException;
import com.afrochow.common.exceptions.GoogleOnlyAccountException;
import com.afrochow.common.exceptions.InvalidCredentialsException;
import com.afrochow.common.exceptions.InvalidTokenException;
import com.afrochow.customer.dto.CustomerProfileRequestDto;
import com.afrochow.email.EmailVerificationTokenRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.security.JwtTokenProvider;
import com.afrochow.security.Services.LoginAttemptService;
import com.afrochow.security.Services.PasswordPolicyService;
import com.afrochow.security.Services.RateLimitService;
import com.afrochow.security.Services.RefreshTokenService;
import com.afrochow.security.Services.SecurityEventService;
import com.afrochow.security.Utils.GeocodingService;
import com.afrochow.security.dto.TokenRefreshResponseDto;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.security.model.EmailVerificationToken;
import com.afrochow.security.model.PasswordResetToken;
import com.afrochow.security.model.RefreshToken;
import com.afrochow.security.repository.PasswordResetTokenRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private RefreshTokenService refreshTokenService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private SecurityEventService securityEventService;
    @Mock private OutboxEventService outboxEventService;
    @Mock private RateLimitService rateLimitService;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private AdminProfileRepository adminProfileRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GeocodingService geocodingService;
    @Mock private AddressRepository addressRepository;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User user;
    private HttpServletRequest httpRequest;
    private HttpServletResponse httpResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authenticationService, "customerWaitlistMode", false);
        ReflectionTestUtils.setField(authenticationService, "passwordResetExpirationMinutes", 60L);
        ReflectionTestUtils.setField(authenticationService, "emailVerificationExpirationMinutes", 1440L);
        ReflectionTestUtils.setField(authenticationService, "frontendUrl", "https://afrochow.ca");
        ReflectionTestUtils.setField(authenticationService, "cookieDomain", "");

        user = User.builder()
                .userId(1L).publicUserId("CUS123").email("customer@example.com")
                .username("adecustomer").password("hashed-current-password")
                .firstName("Ade").lastName("O").phone("4165551234")
                .role(Role.CUSTOMER).authProvider(AuthProvider.EMAIL)
                .isActive(true).emailVerified(true).acceptTerms(true)
                .build();

        httpRequest = mock(HttpServletRequest.class);
        httpResponse = mock(HttpServletResponse.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication mockSuccessfulAuthentication(User u) {
        CustomUserDetails principal = new CustomUserDetails(u, List.of());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }

    // ========== login ==========

    @Test
    void login_success_issuesTokensAndReturnsLoginResponse() {
        Authentication successfulAuth = mockSuccessfulAuthentication(user);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptService.isAccountLocked("customer@example.com")).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenReturn(successfulAuth);
        when(refreshTokenService.createRefreshTokenForUser(eq(user), eq(httpRequest))).thenReturn("raw-refresh-token");
        when(jwtTokenProvider.createToken(user)).thenReturn("raw-access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

        LoginRequest request = LoginRequest.builder().identifier("customer@example.com").password("Secret123!").build();
        LoginResponse response = authenticationService.login(request, httpRequest, httpResponse);

        assertThat(response.getPublicUserId()).isEqualTo("CUS123");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        verify(loginAttemptService).loginSucceeded("customer@example.com", httpRequest);
        verify(userRepository).save(user);
    }

    @Test
    void login_unverifiedEmail_throwsAndNeverIssuesTokens() {
        user.setEmailVerified(false);
        Authentication successfulAuth = mockSuccessfulAuthentication(user);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptService.isAccountLocked("customer@example.com")).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenReturn(successfulAuth);

        LoginRequest request = LoginRequest.builder().identifier("customer@example.com").password("Secret123!").build();

        assertThatThrownBy(() -> authenticationService.login(request, httpRequest, httpResponse))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(refreshTokenService, never()).createRefreshTokenForUser(any(), any());
    }

    @Test
    void login_googleOnlyAccount_throwsBeforeAuthenticating() {
        user.setGoogleId("google-sub-123");
        user.setPassword(null);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptService.isAccountLocked("customer@example.com")).thenReturn(false);

        LoginRequest request = LoginRequest.builder().identifier("customer@example.com").password("whatever").build();

        assertThatThrownBy(() -> authenticationService.login(request, httpRequest, httpResponse))
                .isInstanceOf(GoogleOnlyAccountException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_accountLocked_throwsWithRemainingLockoutSeconds() {
        when(loginAttemptService.isAccountLocked("unknown@example.com")).thenReturn(true);
        when(loginAttemptService.getRemainingLockoutSeconds("unknown@example.com")).thenReturn(120L);

        LoginRequest request = LoginRequest.builder().identifier("unknown@example.com").password("whatever").build();

        assertThatThrownBy(() -> authenticationService.login(request, httpRequest, httpResponse))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(ex -> assertThat(((AccountLockedException) ex).getRemainingLockoutSeconds()).isEqualTo(120L));
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_badCredentials_recordsFailureAndRethrowsGenericMessage() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptService.isAccountLocked("customer@example.com")).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));
        when(loginAttemptService.getAttemptCount("customer@example.com")).thenReturn(3);

        LoginRequest request = LoginRequest.builder().identifier("customer@example.com").password("wrong").build();

        assertThatThrownBy(() -> authenticationService.login(request, httpRequest, httpResponse))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username/email or password");

        verify(loginAttemptService).loginFailed(eq("customer@example.com"), eq(httpRequest));
        verify(securityEventService).logFailedLoginAttempt(eq("customer@example.com"), anyString(), eq(3), eq(httpRequest));
    }

    // ========== registerCustomer ==========

    private CustomerProfileRequestDto customerRegistrationRequest() {
        // Plain @Builder (as opposed to @SuperBuilder) only generates builder methods
        // for fields declared directly on CustomerProfileRequestDto, not the ones it
        // inherits from BaseRegistrationRequest (email, password, firstName, etc.) —
        // so those have to be set via the inherited @Data setters instead.
        CustomerProfileRequestDto request = new CustomerProfileRequestDto();
        request.setEmail("new@example.com");
        request.setPassword("Secret123!");
        request.setConfirmPassword("Secret123!");
        request.setFirstName("New");
        request.setLastName("Customer");
        request.setPhone("4165551111");
        request.setAcceptTerms(true);
        request.setAddress(AddressRequestDto.builder()
                .addressLine("123 Main St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").build());
        return request;
    }

    @Test
    void registerCustomer_waitlistModeEnabled_throwsCustomerWaitlistMode() {
        ReflectionTestUtils.setField(authenticationService, "customerWaitlistMode", true);

        assertThatThrownBy(() -> authenticationService.registerCustomer(customerRegistrationRequest(), httpRequest))
                .isInstanceOf(CustomerWaitlistModeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerCustomer_success_createsUserAndSendsVerification() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("4165551111")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(2L);
            u.setPublicUserId("CUS999");
            return u;
        });

        RegistrationResponse response = authenticationService.registerCustomer(customerRegistrationRequest(), httpRequest);

        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getEmailVerified()).isFalse();
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(outboxEventService).emailVerificationSent(eq("CUS999"), eq("new@example.com"), eq("New"), anyString());
        verify(securityEventService).logRegistration(eq("new@example.com"), eq(httpRequest));
    }

    @Test
    void registerCustomer_emailAlreadyExists_throwsAndNeverSaves() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.registerCustomer(customerRegistrationRequest(), httpRequest))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    // ========== forgotPassword ==========

    @Test
    void forgotPassword_existingUser_sendsResetLinkButReturnsGenericMessage() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        String result = authenticationService.forgotPassword(
                ForgotPasswordRequestDto.builder().identifier("customer@example.com").build(), httpRequest);

        assertThat(result).contains("If the username or email exists");
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(outboxEventService).passwordResetRequested(eq("CUS123"), eq("customer@example.com"), eq("Ade"), anyString());
    }

    @Test
    void forgotPassword_googleOnlyAccount_throws() {
        user.setGoogleId("google-sub-123");
        user.setPassword(null);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.forgotPassword(
                ForgotPasswordRequestDto.builder().identifier("customer@example.com").build(), httpRequest))
                .isInstanceOf(GoogleOnlyAccountException.class);
    }

    @Test
    void forgotPassword_unknownIdentifier_returnsGenericMessageWithoutError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        String result = authenticationService.forgotPassword(
                ForgotPasswordRequestDto.builder().identifier("nobody@example.com").build(), httpRequest);

        assertThat(result).contains("If the username or email exists");
        verify(passwordResetTokenRepository, never()).save(any());
    }

    // ========== resetPassword ==========

    @Test
    void resetPassword_success_updatesPasswordAndRevokesOtherTokens() {
        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L).user(user).tokenHash(PasswordResetToken.hashToken("raw-token-abc"))
                .expiryDate(Instant.now().plusSeconds(3600)).used(false).build();
        when(passwordResetTokenRepository.findByTokenHash(PasswordResetToken.hashToken("raw-token-abc")))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("encoded-new-password");

        authenticationService.resetPassword(
                ResetPasswordRequestDto.builder().token("raw-token-abc").newPassword("NewSecret123!").build(),
                httpRequest);

        verify(passwordResetTokenRepository).revokeAllUserTokens(1L);
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_tokenNotFound_throwsInvalidToken() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.resetPassword(
                ResetPasswordRequestDto.builder().token("bad-token").newPassword("NewSecret123!").build(), httpRequest))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void resetPassword_tokenAlreadyUsed_throwsInvalidToken() {
        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L).user(user).tokenHash(PasswordResetToken.hashToken("raw-token-abc"))
                .expiryDate(Instant.now().plusSeconds(3600)).used(true).build();
        when(passwordResetTokenRepository.findByTokenHash(PasswordResetToken.hashToken("raw-token-abc")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authenticationService.resetPassword(
                ResetPasswordRequestDto.builder().token("raw-token-abc").newPassword("NewSecret123!").build(), httpRequest))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    // ========== changePassword ==========

    @Test
    void changePassword_success_updatesToNewEncodedPassword() {
        when(userRepository.findByPublicUserId("CUS123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pw", "hashed-current-password")).thenReturn(true);
        when(passwordEncoder.matches("New-Secret123!", "hashed-current-password")).thenReturn(false);
        when(passwordEncoder.encode("New-Secret123!")).thenReturn("encoded-new-password");

        authenticationService.changePassword("CUS123", "current-pw", "New-Secret123!", httpRequest);

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        verify(outboxEventService).passwordChanged("CUS123", "customer@example.com", "Ade");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsInvalidCredentials() {
        when(userRepository.findByPublicUserId("CUS123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pw", "hashed-current-password")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.changePassword("CUS123", "wrong-pw", "New-Secret123!", httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_newPasswordSameAsCurrent_throwsIllegalArgument() {
        when(userRepository.findByPublicUserId("CUS123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pw", "hashed-current-password")).thenReturn(true);
        when(passwordEncoder.matches("current-pw-again", "hashed-current-password")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.changePassword(
                "CUS123", "current-pw", "current-pw-again", httpRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");
    }

    // ========== verifyEmail ==========

    @Test
    void verifyEmail_success_marksVerifiedAndFiresRegisteredEvent() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenId(1L).token("123456").user(user)
                .expiresAt(Instant.now().plusSeconds(3600)).isUsed(false).build();
        when(emailVerificationTokenRepository.findValidToken(eq("123456"), eq("customer@example.com"), any(Instant.class)))
                .thenReturn(Optional.of(token));

        String result = authenticationService.verifyEmail("customer@example.com", "123456");

        assertThat(result).contains("verified successfully");
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(token.getIsUsed()).isTrue();
        verify(outboxEventService).userRegistered("CUS123", "customer@example.com", "Ade", "CUSTOMER");
    }

    @Test
    void verifyEmail_vendorWithCompleteProfile_advancesToPendingReview() {
        User vendorUser = User.builder()
                .userId(7L)
                .publicUserId("VEN123")
                .email("vendor@example.com")
                .firstName("Vendor")
                .role(Role.VENDOR)
                .emailVerified(false)
                .build();
        VendorProfile vendorProfile = VendorProfile.builder()
                .user(vendorUser)
                .restaurantName("Vendor Kitchen")
                .storeCategory("West African")
                .logoUrl("https://cdn.example.com/logo.png")
                .offersPickup(true)
                .address(Address.builder()
                        .addressLine("123 Main St")
                        .city("Calgary")
                        .province(Province.AB)
                        .postalCode("T2P1J9")
                        .build())
                .vendorStatus(VendorStatus.PENDING_PROFILE)
                .build();
        vendorProfile.setOperatingHours(Map.of(
                "monday", new VendorProfile.DayHours(true, "09:00", "17:00")));
        vendorUser.setVendorProfile(vendorProfile);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenId(1L).token("123456").user(vendorUser)
                .expiresAt(Instant.now().plusSeconds(3600)).isUsed(false).build();
        when(emailVerificationTokenRepository.findValidToken(eq("123456"), eq("vendor@example.com"), any(Instant.class)))
                .thenReturn(Optional.of(token));

        authenticationService.verifyEmail("vendor@example.com", "123456");

        assertThat(vendorUser.getEmailVerified()).isTrue();
        assertThat(vendorProfile.getVendorStatus()).isEqualTo(VendorStatus.PENDING_REVIEW);
        verify(vendorProfileRepository).save(vendorProfile);
        verify(outboxEventService).userRegistered("VEN123", "vendor@example.com", "Vendor", "VENDOR");
    }

    @Test
    void verifyEmail_invalidCode_throwsBadCredentials() {
        when(emailVerificationTokenRepository.findValidToken(eq("000000"), eq("customer@example.com"), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.verifyEmail("customer@example.com", "000000"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ========== resendVerificationEmail ==========

    @Test
    void resendVerificationEmail_alreadyVerified_isNoOp() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user)); // already emailVerified=true

        authenticationService.resendVerificationEmail("customer@example.com");

        verify(emailVerificationTokenRepository, never()).save(any());
    }

    @Test
    void resendVerificationEmail_notVerified_revokesOldAndSendsNewToken() {
        user.setEmailVerified(false);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        authenticationService.resendVerificationEmail("customer@example.com");

        // Called once directly by resendVerificationEmail() and once more inside
        // createAndSendEmailVerificationToken() — redundant in the real code, but
        // that's the actual current behavior, so assert it accurately.
        verify(emailVerificationTokenRepository, org.mockito.Mockito.times(2)).revokeAllUserTokens(1L);
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(outboxEventService).emailVerificationSent(eq("CUS123"), eq("customer@example.com"), eq("Ade"), anyString());
    }

    // ========== logout ==========

    @Test
    void logout_revokesRefreshTokenFromCookieAndClearsAuthCookies() {
        jakarta.servlet.http.Cookie refreshCookie = new jakarta.servlet.http.Cookie(
                com.afrochow.security.Utils.CookieConstants.REFRESH_TOKEN_COOKIE, "raw-refresh-token");
        when(httpRequest.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{refreshCookie});

        authenticationService.logout(httpRequest, httpResponse);

        verify(refreshTokenService).revokeToken("raw-refresh-token");
    }

    // ========== refreshTokenFromCookie ==========

    @Test
    void refreshTokenFromCookie_success_rotatesAndReturnsDto() {
        RefreshToken existingToken = RefreshToken.builder().id(1L).token("old-raw-token").user(user).build();
        when(refreshTokenService.verifyRefreshToken("old-raw-token")).thenReturn(existingToken);
        when(refreshTokenService.rotateRefreshToken(eq("old-raw-token"), eq(httpRequest))).thenReturn("new-raw-token");
        when(jwtTokenProvider.createToken(user)).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

        TokenRefreshResponseDto response =
                authenticationService.refreshTokenFromCookie("old-raw-token", httpRequest, httpResponse);

        assertThat(response.getPublicUserId()).isEqualTo("CUS123");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
    }
}
