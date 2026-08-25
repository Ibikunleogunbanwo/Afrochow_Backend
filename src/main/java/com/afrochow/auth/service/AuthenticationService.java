package com.afrochow.auth.service;

import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.admin.dto.AdminProfileRequestDto;
import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.admin.model.AdminProfile;
import com.afrochow.admin.repository.AdminProfileRepository;
import com.afrochow.auth.dto.BaseRegistrationRequestDto;
import com.afrochow.auth.dto.ForgotPasswordRequestDto;
import com.afrochow.auth.dto.LoginRequestDto;
import com.afrochow.auth.dto.LoginResponseDto;
import com.afrochow.auth.dto.RegistrationResponseDto;
import com.afrochow.auth.dto.ResetPasswordRequestDto;
import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.AuthProvider;
import com.afrochow.common.enums.Role;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.common.exceptions.*;
import com.afrochow.customer.dto.CustomerProfileRequestDto;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.email.repository.EmailVerificationTokenRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.security.JwtTokenProvider;
import com.afrochow.security.service.*;
import com.afrochow.security.util.CookieConstants;
import com.afrochow.security.util.CookieUtils;
import com.afrochow.security.service.GeocodingService;
import com.afrochow.security.util.SecurityUtils;
import com.afrochow.security.dto.TokenRefreshResponseDto;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.email.model.EmailVerificationToken;
import com.afrochow.security.model.PasswordResetToken;
import com.afrochow.security.model.RefreshToken;
import com.afrochow.security.repository.PasswordResetTokenRepository;
import com.afrochow.user.dto.UserCustomerSummaryDto;
import com.afrochow.user.mapper.UserMapper;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.dto.VendorProfileRequestDto;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final RefreshTokenService           refreshTokenService;
    private final JwtTokenProvider              jwtTokenProvider;
    private final LoginAttemptService           loginAttemptService;
    private final AuthenticationManager         authenticationManager;
    private final SecurityEventService          securityEventService;
    private final OutboxEventService            outboxEventService;
    private final RateLimitService              rateLimitService;
    private final PasswordPolicyService         passwordPolicyService;
    private final UserRepository                userRepository;
    private final UserMapper                    userMapper;
    private final PasswordResetTokenRepository  passwordResetTokenRepository;
    private final VendorProfileRepository       vendorProfileRepository;
    private final AdminProfileRepository        adminProfileRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder               passwordEncoder;

    // See CustomerWaitlistModeException — blocks new CUSTOMER account creation
    // while the platform is pre-launch for customers. Mirrors the frontend's
    // NEXT_PUBLIC_CUSTOMER_MODE flag (src/lib/mvp.js); vendor registration is
    // unaffected.
    @Value("${app.customer-waitlist-mode:true}")
    private boolean customerWaitlistMode;
    private final GeocodingService geocodingService;
    private final AddressRepository addressRepository;

    @Value("${security.password-reset.expiration-minutes:60}")
    private long passwordResetExpirationMinutes;

    @Value("${security.email-verification.expiration-minutes:1440}")
    private long emailVerificationExpirationMinutes;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.cookie.domain:}")
    private String cookieDomain;


    /* ==========================================================
       GET CURRENT USER
       ========================================================== */

    public UserCustomerSummaryDto getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (!(authentication.getPrincipal() instanceof CustomUserDetails principal)) return null;

        User user = principal.getUser();
        if (user == null) return null;

        boolean profileComplete = user.getPhone() != null && !user.getPhone().isBlank();
        String authProvider = user.getAuthProvider() != null ? user.getAuthProvider().name() : AuthProvider.EMAIL.name();

        return UserCustomerSummaryDto.builder()
                .publicUserId(user.getPublicUserId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole().name())
                .isActive(user.getIsActive())
                .isProfileComplete(profileComplete)
                .profileImageUrl(user.getProfileImageUrl())
                .authProvider(authProvider)
                .build();
    }


    /* ==========================================================
       LOGIN
       ========================================================== */

    /**
     * Authenticate user and issue JWT tokens via HttpOnly cookies.
     *
     * Security features:
     * - Rate limiting per IP + identifier
     * - Account lockout protection
     * - Email verification requirement
     * - Failed attempt tracking and audit logging
     */
    @Transactional
    public LoginResponseDto login(
            LoginRequestDto loginRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String identifier = loginRequest.getIdentifier().trim();
        String password   = loginRequest.getPassword().trim();
        String clientIp   = SecurityUtils.getClientIP(httpRequest);

        // Resolve to canonical email so lockout counters are always keyed by
        // email, regardless of whether the user typed their email or username.
        // Falls back to the raw identifier for non-existent accounts so
        // brute-force attempts against unknown identifiers are still tracked.
        String canonicalEmail = findUserByIdentifier(identifier)
                .map(User::getEmail)
                .orElse(identifier);

        rateLimitService.verifyLoginLimit(clientIp + ":" + identifier);
        validateAccountNotLocked(canonicalEmail, clientIp);

        // Reactivate account if within the 30-day grace period
        reactivateIfEligible(identifier, password);

        // Block password login for Google-only accounts
        findUserByIdentifier(identifier).ifPresent(user -> {
            if (user.getGoogleId() != null && user.getPassword() == null) {
                throw new GoogleOnlyAccountException(
                        "This account was created with Google. Please sign in with Google.");
            }
        });

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, password)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            if (principal == null || principal.getUser() == null) {
                throw new IllegalStateException("Authenticated principal does not contain a User entity");
            }

            User user = principal.getUser();

            if (!user.getEmailVerified()) {
                log.warn("Login attempt with unverified email: {}", user.getEmail());
                throw new EmailNotVerifiedException(
                        "Email not verified. Please check your email and verify your account before logging in.",
                        user.getEmail()
                );
            }

            String refreshToken = refreshTokenService.createRefreshTokenForUser(user, httpRequest);
            String accessToken  = jwtTokenProvider.createToken(user);

            CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.ACCESS_TOKEN_COOKIE,
                    accessToken, jwtTokenProvider.getAccessTokenExpirationSeconds(), true, "None", cookieDomain);
            CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE,
                    refreshToken, refreshTokenService.getRefreshTokenExpirationSeconds(), true, "None", cookieDomain);

            loginAttemptService.loginSucceeded(user.getEmail(), httpRequest);

            // Stamp last login time
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            return buildLoginResponse(user);

        } catch (DisabledException e) {
            throw new DisabledAccountException("Please verify your e-mail.");
        } catch (LockedException e) {
            long remainingSeconds = loginAttemptService.getRemainingLockoutSeconds(canonicalEmail);
            throw new AccountLockedException("Account locked due to too many failed attempts.", remainingSeconds);
        } catch (BadCredentialsException e) {
            loginAttemptService.loginFailed(canonicalEmail, httpRequest);
            int attemptCount = loginAttemptService.getAttemptCount(canonicalEmail);
            securityEventService.logFailedLoginAttempt(identifier, clientIp, attemptCount, httpRequest);
            throw new BadCredentialsException("Invalid username/email or password");
        }
    }


    /* ==========================================================
       TOKEN REFRESH
       ========================================================== */

    /**
     * Rotate refresh token and issue a new access token via HttpOnly cookies.
     * Security features: token rotation, IP tracking, reuse detection.
     */
    @Transactional
    public TokenRefreshResponseDto refreshTokenFromCookie(
            String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String clientIp = SecurityUtils.getClientIP(httpRequest);

        RefreshToken validToken = refreshTokenService.verifyRefreshToken(refreshToken);
        User user               = validToken.getUser();
        String newRefreshToken  = refreshTokenService.rotateRefreshToken(refreshToken, httpRequest);
        String newAccessToken   = jwtTokenProvider.createToken(user);

        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.ACCESS_TOKEN_COOKIE,
                newAccessToken, jwtTokenProvider.getAccessTokenExpirationSeconds(), true, "None", cookieDomain);
        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE,
                newRefreshToken, refreshTokenService.getRefreshTokenExpirationSeconds(), true, "None", cookieDomain);

        log.info("Token refreshed for user: {} from IP: {}", user.getPublicUserId(), clientIp);

        return TokenRefreshResponseDto.builder()
                .publicUserId(user.getPublicUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }


    /* ==========================================================
       LOGOUT
       ========================================================== */

    /**
     * Revoke the current device's refresh token and clear auth cookies.
     */
    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (CookieConstants.REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    refreshTokenService.revokeToken(cookie.getValue());
                    log.info("Refresh token revoked on logout");
                }
            }
        }
        clearAuthCookies(httpResponse);
        log.info("User logged out (cookies cleared)");
    }

    /**
     * Revoke all refresh tokens for the authenticated user (logout from all devices).
     */
    @Transactional
    public void logoutAllDevices(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) throw new UnauthorizedException("Not authenticated");

        String clientIp = SecurityUtils.getClientIP(httpRequest);
        refreshTokenService.revokeAllUserTokens(username);
        log.info("User {} logged out from all devices (IP: {})", username, clientIp);

        clearAuthCookies(httpResponse);
    }

    /**
     * Password-gated variant used by the user-facing "Sign out of all devices"
     * control. Verifying the password here prevents a leaked access token from
     * being used to lock the account owner out of every session.
     */
    @Transactional
    public void logoutAllDevicesWithPasswordCheck(String publicUserId,
                                                  String password,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password must be provided");
        }
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            // Audit the failed attempt — same log type we use for password
            // mismatches elsewhere so ops can correlate brute-force activity.
            securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);
            throw new InvalidCredentialsException("Password is incorrect");
        }

        String clientIp = SecurityUtils.getClientIP(httpRequest);
        // revokeAllUserTokens currently keys off username; fall back to email for
        // email-registered users whose username column is null.
        String tokenKey = (user.getUsername() != null && !user.getUsername().isBlank())
                ? user.getUsername()
                : user.getEmail();
        refreshTokenService.revokeAllUserTokens(tokenKey);
        log.info("User {} logged out from all devices (IP: {})", user.getPublicUserId(), clientIp);

        clearAuthCookies(httpResponse);
    }


    /* ==========================================================
       REGISTRATION
       ========================================================== */

    /**
     * Register a new customer. Sends email verification — login blocked until verified.
     */
    @Transactional
    public RegistrationResponseDto registerCustomer(
            CustomerProfileRequestDto request,
            HttpServletRequest httpRequest
    ) {
        if (customerWaitlistMode) {
            throw new CustomerWaitlistModeException(
                    "Afrochow ordering is opening soon. Join the waitlist and we will let you know when customer accounts go live.");
        }

        String clientIp = SecurityUtils.getClientIP(httpRequest);
        rateLimitService.verifyRegistrationLimit(clientIp);
        validateRegistrationData(request.getEmail(), request.getUsername(), request.getPhone());

        try {
            User savedUser = userRepository.save(createUser(request, Role.CUSTOMER));
            createCustomerProfile(savedUser, request);
            createAndSendEmailVerificationToken(savedUser);
            securityEventService.logRegistration(savedUser.getEmail(), httpRequest);
            return registrationResponse(savedUser);
        } catch (DataIntegrityViolationException ex) {
            throw translateRegistrationDuplicate(ex);
        }
    }

    /**
     * Register a new vendor. Validates business rules before persisting.
     * Sends email verification — login blocked until verified.
     */
    @Transactional
    public RegistrationResponseDto registerVendor(
            VendorProfileRequestDto request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = SecurityUtils.getClientIP(httpRequest);
        rateLimitService.verifyRegistrationLimit(clientIp);
        validateRegistrationData(request.getEmail(), request.getUsername(), request.getPhone());
        validateVendorRequest(request);

        try {
            User savedUser = userRepository.save(createUser(request, Role.VENDOR));
            createVendorProfile(savedUser, request);
            createAndSendEmailVerificationToken(savedUser);
            securityEventService.logRegistration(savedUser.getEmail(), httpRequest);
            return registrationResponse(savedUser);
        } catch (DataIntegrityViolationException ex) {
            throw translateRegistrationDuplicate(ex);
        }
    }

    /**
     * Register a new admin. Only callable by an existing SUPER_ADMIN.
     * No email verification required.
     */
    @Transactional
    public AdminProfileResponseDto registerAdmin(
            AdminProfileRequestDto request,
            HttpServletRequest httpRequest
    ) {
        Long requestingAdminId = SecurityUtils.getCurrentUserId();
        if (requestingAdminId == null) {
            throw new UnauthorizedException("Authentication required to create admin accounts");
        }

        User requestingAdmin = userRepository.findById(requestingAdminId)
                .orElseThrow(() -> new UnauthorizedException("Admin user not found"));

        // Gate on the real, enforced role — not AdminProfile.accessLevel, which is
        // a legacy display-only field that can drift out of sync with User.role
        // (e.g. after a promote/demote via SuperAdminController). This also keeps
        // this service-level check consistent with the @PreAuthorize("hasRole('SUPERADMIN')")
        // on AuthController and the matching SecurityConfig filter-chain rule for
        // this same endpoint.
        if (requestingAdmin.getRole() != Role.SUPERADMIN) {
            throw new InsufficientPermissionException("Only SUPERADMIN can create admin accounts");
        }

        validateRegistrationData(request.getEmail(), request.getUsername(), request.getPhone());

        try {
            User savedUser = userRepository.save(createUser(request, Role.ADMIN));
            createAdminProfile(savedUser, request);

            log.info("Admin account created by SUPER_ADMIN: {} (userId: {})",
                    requestingAdmin.getEmail(), requestingAdminId);
            securityEventService.logRegistration(savedUser.getEmail(), httpRequest);
            return adminProfileResponseDto(savedUser);
        } catch (DataIntegrityViolationException ex) {
            throw translateRegistrationDuplicate(ex);
        }
    }


    /* ==========================================================
       PASSWORD RESET
       ========================================================== */

    /**
     * Initiate a password reset. Returns a generic message to prevent email enumeration.
     */
    @Transactional
    public String forgotPassword(ForgotPasswordRequestDto request, HttpServletRequest httpRequest) {
        String identifier = request.getIdentifier().trim();
        rateLimitService.verifyPasswordResetLimit(getCanonicalIdentifier(identifier));
        findUserByIdentifier(identifier).ifPresent(user -> {
            if (user.getGoogleId() != null && user.getPassword() == null) {
                throw new GoogleOnlyAccountException(
                        "This account uses Google login. Password reset is not available. Please sign in with Google.");
            }
            processPasswordResetRequest(user, httpRequest);
        });
        return "If the username or email exists in our system, you will receive reset instructions.";
    }

    /**
     * Reset password using the token received via email.
     *
     * Uses SHA-256 for indexed DB lookup — see PasswordResetToken.hashToken().
     * All other reset tokens for the user are revoked on success to
     * prevent reuse of any other outstanding reset links.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequestDto request, HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(request.getToken()) || !StringUtils.hasText(request.getNewPassword())) {
            throw new IllegalArgumentException("Token and new password must be provided");
        }

        PasswordResetToken tokenEntity = passwordResetTokenRepository
                .findByTokenHash(PasswordResetToken.hashToken(request.getToken()))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        if (!tokenEntity.isValid()) {
            throw new InvalidTokenException("Token expired or already used");
        }

        User user = tokenEntity.getUser();

        // Enforce same password policy as registration
        passwordPolicyService.validatePassword(request.getNewPassword());

        // Revoke all reset tokens for this user — prevents reuse of other outstanding links
        passwordResetTokenRepository.revokeAllUserTokens(user.getUserId());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);
    }

    /**
     * Change password for an already-authenticated user.
     *
     * <p>Requires the caller to prove knowledge of the current password — this
     * prevents session-hijack-based account takeover (a stolen cookie alone
     * cannot change the password).
     *
     * <p>Note: we do NOT apply the password-reset rate limit here; that bucket
     * is for unauthenticated "forgot password" flows. Authenticated password
     * changes are naturally rate-limited by requiring the current password.
     */
    @Transactional
    public void changePassword(String publicUserId,
                               String currentPassword,
                               String newPassword,
                               HttpServletRequest httpRequest) {

        if (!StringUtils.hasText(currentPassword)) {
            throw new IllegalArgumentException("Current password must be provided");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new IllegalArgumentException("New password must be provided");
        }

        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Verify the caller knows the current password BEFORE doing anything else
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        // Enforce the same password policy as registration
        passwordPolicyService.validatePassword(newPassword);

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);
        outboxEventService.passwordChanged(user.getPublicUserId(), user.getEmail(), user.getFirstName());
    }


    /* ==========================================================
       EMAIL VERIFICATION
       ========================================================== */

    /**
     * Verify email address using the 6-digit code received via email.
     *
     * Requires the email alongside the code (not code alone) and is rate-limited
     * per email — a bare 6-digit code is only ~1,000,000 possibilities, and
     * without both of those guards an attacker could brute-force any pending
     * user's code with unlimited, unscoped guesses. See findValidToken()'s
     * Javadoc for the scoping rationale.
     */
    @Transactional
    public String verifyEmail(String email, String code) {
        String canonicalEmail = getCanonicalIdentifier(email);
        rateLimitService.verifyEmailVerificationLimit(canonicalEmail);

        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findValidToken(code, email, Instant.now())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired verification code"));

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        advanceVerifiedVendorProfileToReview(user);

        outboxEventService.userRegistered(
                user.getPublicUserId(), user.getEmail(),
                user.getFirstName(), user.getRole().name());

        verificationToken.markAsUsed();
        emailVerificationTokenRepository.save(verificationToken);
        userRepository.save(user);

        rateLimitService.resetLimit("email-verify", canonicalEmail);

        log.info("Email verified for user: {}", user.getPublicUserId());
        return "Email verified successfully. You can now login to your account.";
    }

    private void advanceVerifiedVendorProfileToReview(User user) {
        if (!user.isVendor() || user.getVendorProfile() == null) {
            return;
        }

        VendorProfile vendorProfile = user.getVendorProfile();
        if (vendorProfile.getVendorStatus() == VendorStatus.PENDING_PROFILE
                && isVendorProfileComplete(vendorProfile)) {
            vendorProfile.setVendorStatus(VendorStatus.PENDING_REVIEW);
            vendorProfile.setIsActive(true);
            vendorProfile.setIsVerified(false);
            vendorProfileRepository.save(vendorProfile);
        }
    }

    /**
     * Resend email verification link. No-op if already verified.
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        rateLimitService.verifyEmailVerificationLimit(getCanonicalIdentifier(email));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (user.getEmailVerified()) return;

        emailVerificationTokenRepository.revokeAllUserTokens(user.getUserId());
        createAndSendEmailVerificationToken(user);
    }

    /**
     * Correct an email typo for an account that has not completed verification.
     * Verified accounts must use the authenticated account-settings flow so that
     * a signed-in user proves ownership of the account before changing identity.
     */
    @Transactional
    public void changeVerificationEmail(String currentEmail, String newEmail) {
        String canonicalCurrentEmail = getCanonicalIdentifier(currentEmail.trim());
        String canonicalNewEmail = getCanonicalIdentifier(newEmail.trim());

        if (canonicalCurrentEmail.equals(canonicalNewEmail)) {
            return;
        }

        User user = userRepository.findByEmail(canonicalCurrentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + canonicalCurrentEmail));

        if (user.getEmailVerified()) {
            throw new IllegalStateException("Email is already verified. Please change it from account settings.");
        }

        if (userRepository.existsByEmail(canonicalNewEmail)) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        emailVerificationTokenRepository.revokeAllUserTokens(user.getUserId());
        user.setEmail(canonicalNewEmail);
        userRepository.save(user);
        createAndSendEmailVerificationToken(user);

        rateLimitService.resetLimit("email-verify", canonicalCurrentEmail);
        rateLimitService.resetLimit("email-verify", canonicalNewEmail);
    }


    /* ==========================================================
       PRIVATE HELPERS
       ========================================================== */

    private void validateAccountNotLocked(String identifier, String clientIp) {
        if (loginAttemptService.isAccountLocked(identifier)) {
            long remainingSeconds = loginAttemptService.getRemainingLockoutSeconds(identifier);
            long remainingMinutes = (remainingSeconds + 59) / 60;
            log.warn("Login attempt for locked identifier: {} from IP: {}", identifier, clientIp);
            securityEventService.logLockedAccountAttempt(identifier, clientIp, remainingMinutes);
            throw new AccountLockedException(
                    String.format("Account is locked. Try again in %d minutes.", remainingMinutes),
                    remainingSeconds
            );
        }
    }

    private void validateRegistrationData(String email, String username, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        if (StringUtils.hasText(phone) && userRepository.existsByPhone(phone)) {
            throw new PhoneNumberAlreadyExistsException("Phone number already registered");
        }
        if (StringUtils.hasText(username) && userRepository.existsByUsername(username)) {
            throw new UserNameAlreadyExistsException("Username already exists");
        }
    }

    /**
     * Translates a DB unique-constraint violation into the proper 409 business
     * exception. The existsBy pre-checks above are a fast-path convenience — they
     * do not close the race window between two concurrent registrations for the
     * same email/phone/username. The DB's unique constraints are the real guard;
     * when they trip, surface a clean 409 instead of the generic 500 that
     * GlobalExceptionHandler would otherwise produce. Mirrors the race-window
     * handling in FavoriteService.
     */
    private RuntimeException translateRegistrationDuplicate(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        String lower = cause.toLowerCase();
        if (lower.contains("phone")) {
            return new PhoneNumberAlreadyExistsException("Phone number already registered");
        }
        if (lower.contains("username")) {
            return new UserNameAlreadyExistsException("Username already exists");
        }
        return new EmailAlreadyExistsException("Email already exists");
    }

    private void validateVendorRequest(VendorProfileRequestDto request) {
        if (request.getDeliveryFee() != null &&
                request.getDeliveryFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Delivery fee cannot be negative");
        }

        if (!Boolean.TRUE.equals(request.getOffersDelivery()) &&
                !Boolean.TRUE.equals(request.getOffersPickup())) {
            throw new IllegalArgumentException("Vendor must offer at least delivery or pickup");
        }

        boolean anyDayOpen = request.getOperatingHours() != null &&
                request.getOperatingHours().values().stream()
                        .anyMatch(d -> Boolean.TRUE.equals(d.getIsOpen()));

        if (!anyDayOpen) {
            throw new IllegalArgumentException("Vendor must be open at least one day per week");
        }
    }

    private User createUser(BaseRegistrationRequestDto request, Role role) {
        passwordPolicyService.validatePassword(request.getPassword());
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(role)
                .authProvider(AuthProvider.EMAIL)
                .profileImageUrl(request.getProfileImageUrl())
                .isActive(true)
                .acceptTerms(request.getAcceptTerms())
                .emailVerified(false)
                .build();
    }

    private void createCustomerProfile(User user, CustomerProfileRequestDto request) {
        CustomerProfile customerProfile = CustomerProfile.builder()
                .defaultDeliveryInstructions(request.getDefaultDeliveryInstructions())
                .build();
        customerProfile.setUser(user);
        user.setCustomerProfile(customerProfile);

        Address address = Address.builder()
                .addressLine(request.getAddress().getAddressLine())
                .city(request.getAddress().getCity())
                .province(request.getAddress().getProvince())
                .postalCode(request.getAddress().getPostalCode())
                .country(request.getAddress().getCountry())
                .defaultAddress(request.getAddress().getDefaultAddress())
                .build();

        customerProfile.addAddress(address);
    }

    private void createVendorProfile(User user, VendorProfileRequestDto request) {

        if (user.getVendorProfile() != null) {
            throw new IllegalStateException("User already has a vendor profile");
        }

        if (request.getAddress() == null) {
            throw new IllegalArgumentException("Address is required");
        }

        Address address = Address.builder()
                .addressLine(request.getAddress().getAddressLine())
                .city(request.getAddress().getCity())
                .province(request.getAddress().getProvince())
                .postalCode(request.getAddress().getPostalCode())
                .country(request.getAddress().getCountry())
                .defaultAddress(request.getAddress().getDefaultAddress())
                .build();

        // Geocode address
        String formatted = address.getFormattedAddress();
        double[] coords = geocodingService.geocode(formatted);

        if (coords != null) {
            address.setLatitude(coords[0]);
            address.setLongitude(coords[1]);
        }

        addressRepository.save(address);

        String timezone = VendorProfile.getTimezoneFromProvince(request.getAddress().getProvince());

        Map<String, VendorProfile.DayHours> operatingHours =
                convertOperatingHours(request.getOperatingHours());

        operatingHours.values().forEach(d -> {
            if (Boolean.TRUE.equals(d.getIsOpen())) {
                LocalTime open  = LocalTime.parse(d.getOpenTime());
                LocalTime close = LocalTime.parse(d.getCloseTime());

                if (!close.isAfter(open)) {
                    throw new IllegalArgumentException(
                            "Closing time must be after opening time for all open days"
                    );
                }
            }
        });

        VendorProfile vendorProfile = VendorProfile.builder()
                .user(user)
                .restaurantName(request.getRestaurantName())
                .description(request.getDescription())
                .storeCategory(request.getStoreCategory())
                .logoUrl(request.getLogoUrl())
                .bannerUrl(request.getBannerUrl())
                .businessLicenseUrl(request.getBusinessLicenseUrl())
                .taxId(request.getTaxId())
                .isVerified(false)
                .isActive(false)
                .offersDelivery(request.getOffersDelivery())
                .offersPickup(request.getOffersPickup())
                .preparationTime(request.getPreparationTime())
                .deliveryFee(request.getDeliveryFee())
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .estimatedDeliveryMinutes(request.getEstimatedDeliveryMinutes())
                .maxDeliveryDistanceKm(request.getMaxDeliveryDistanceKm())
                .address(address)
                .timezone(timezone)
                .build();

        vendorProfile.setOperatingHours(operatingHours);

        // Email verification gates admin review. A complete registration stays
        // PENDING_PROFILE until verifyEmail() marks the owner email as verified.
        // Otherwise the @Builder.Default leaves it at PENDING_PROFILE.

        user.setVendorProfile(vendorProfile);

        vendorProfileRepository.save(vendorProfile);
    }

    /**
     * Mirrors the completeness check in {@code VendorProfileService.isProfileComplete()}.
     * Keep both in sync if the criteria ever change.
     *
     * Required: restaurant name, store category, logo, at least one service
     * (delivery or pickup), a saved address, and at least one open operating day.
     */
    private boolean isVendorProfileComplete(VendorProfile profile) {
        return profile.getRestaurantName() != null && !profile.getRestaurantName().isBlank()
                && profile.getStoreCategory()    != null && !profile.getStoreCategory().isBlank()
                && profile.getLogoUrl()        != null && !profile.getLogoUrl().isBlank()
                && (Boolean.TRUE.equals(profile.getOffersDelivery())
                        || Boolean.TRUE.equals(profile.getOffersPickup()))
                && hasCompleteAddress(profile.getAddress())
                && profile.hasOperatingDays();
    }

    private boolean hasCompleteAddress(Address address) {
        return address != null
                && address.getAddressLine() != null && !address.getAddressLine().isBlank()
                && address.getCity() != null && !address.getCity().isBlank()
                && address.getProvince() != null
                && address.getPostalCode() != null && !address.getPostalCode().isBlank();
    }

    /**
     * Creates the AdminProfile row for a newly-registered admin.
     *
     * <p>{@code accessLevel} is always MODERATOR and every {@code can*} flag is
     * always false here, regardless of what the request might send — these are
     * legacy, unenforced fields (see {@link AdminProfile#isSuperAdmin()}). Real
     * SUPERADMIN status is granted exclusively via
     * {@code SuperAdminController.promoteToSuperAdmin}, which sets {@code User.role}
     * directly and is itself SUPERADMIN-gated. Letting this endpoint accept an
     * arbitrary {@code accessLevel}/{@code can*} combination used to let a
     * requesting SUPERADMIN create an account that displayed as "Super Admin —
     * full access" while actually being a plain ADMIN under the hood — a
     * misleading, since-fixed bug.
     */
    private void createAdminProfile(User user, AdminProfileRequestDto request) {
        AdminProfile adminProfile = AdminProfile.builder()
                .user(user)
                .department(request.getDepartment())
                .accessLevel(AdminAccessLevel.MODERATOR)
                .employeeId(request.getEmployeeId())
                .build();

        user.setAdminProfile(adminProfile);
        adminProfileRepository.save(adminProfile);
    }

    @Transactional
    public void processPasswordResetRequest(User user, HttpServletRequest httpRequest) {
        passwordResetTokenRepository.revokeAllUserTokens(user.getUserId());

        PasswordResetToken token = PasswordResetToken.create(
                user,
                passwordResetExpirationMinutes,
                SecurityUtils.getClientIP(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        passwordResetTokenRepository.save(token);

        // Must match the actual frontend route — src/app/(auth)/reset-password/page.jsx
        // reads ?token= there. This previously pointed at /customer/confirm-token,
        // a route that has never existed, so every password-reset email sent a
        // customer straight to a 404 instead of the reset form.
        String resetLink = frontendUrl + "/reset-password?token=" + token.getTransientRawToken();
        securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);
        outboxEventService.passwordResetRequested(
                user.getPublicUserId(), user.getEmail(), user.getFirstName(), resetLink);
    }

    private void createAndSendEmailVerificationToken(User user) {
        emailVerificationTokenRepository.revokeAllUserTokens(user.getUserId());

        EmailVerificationToken verificationToken = EmailVerificationToken.create(
                user, emailVerificationExpirationMinutes
        );
        emailVerificationTokenRepository.save(verificationToken);

        // Write to outbox — the poller delivers the email asynchronously, so an
        // SMTP blip never rolls back registration. The token is already persisted;
        // the user can also resend via POST /auth/resend-verification.
        outboxEventService.emailVerificationSent(
                user.getPublicUserId(), user.getEmail(),
                user.getFirstName(), verificationToken.getToken());

        log.info("Email verification outbox event queued for user: {}", user.getPublicUserId());
    }

    private void clearAuthCookies(HttpServletResponse httpResponse) {
        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.ACCESS_TOKEN_COOKIE, "", 0, true, "None", cookieDomain);
        CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE, "", 0, true, "None", cookieDomain);
    }

    private LoginResponseDto buildLoginResponse(User user) {
        return userMapper.toLoginResponse(user);
    }

    private AdminProfileResponseDto adminProfileResponseDto(User user) {
        AdminProfile profile = user.getAdminProfile();
        boolean profileComplete = user.getPhone() != null && !user.getPhone().isBlank();
        String authProvider = user.getAuthProvider() != null ? user.getAuthProvider().name() : AuthProvider.EMAIL.name();
        return AdminProfileResponseDto.builder()
                .publicUserId(user.getPublicUserId())
                .department(profile.getDepartment())
                .accessLevel(profile.getAccessLevel())
                .username(user.getUsername())
                .employeeId(profile.getEmployeeId())
                .isSuperAdmin(profile.isSuperAdmin())
                .hasFullAccess(profile.hasFullAccess())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profileImageUrl(user.getProfileImageUrl())
                .isProfileComplete(profileComplete)
                .authProvider(authProvider)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private RegistrationResponseDto registrationResponse(User user) {
        return RegistrationResponseDto.builder()
                .message("Registration successful!")
                .email(user.getEmail())
                .publicUserId(user.getPublicUserId())
                .emailVerified(false)
                .nextStep("Please check your email to verify your account before logging in.")
                .build();
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        return identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(identifier);
    }

    /**
     * If the account was soft-deleted within the last 30 days and the supplied
     * password is correct, reactivate it so Spring Security sees it as enabled.
     * Must run BEFORE authenticationManager.authenticate().
     */
    private void reactivateIfEligible(String identifier, String password) {
        userRepository.findByUsernameOrEmail(identifier).ifPresent(user -> {
            if (Boolean.FALSE.equals(user.getIsActive())
                    && user.getScheduledForDeletionAt() != null
                    && user.getScheduledForDeletionAt().isAfter(LocalDateTime.now().minusDays(30))
                    && passwordEncoder.matches(password, user.getPassword())) {
                user.setIsActive(true);
                user.setScheduledForDeletionAt(null);
                userRepository.save(user);
                log.info("Account reactivated on login for user: {}", user.getEmail());
            }
        });
    }

    private String getCanonicalIdentifier(String identifier) {
        return identifier.contains("@") ? identifier.toLowerCase() : identifier;
    }

    /**
     * Convert DTO operating hours to entity format.
     * Keys are lowercased here — no further normalization needed after this call.
     */
    private Map<String, VendorProfile.DayHours> convertOperatingHours(
            Map<String, VendorProfileRequestDto.DayHoursDto> dtoHours
    ) {
        if (dtoHours == null) return getDefaultOperatingHours();

        Map<String, VendorProfile.DayHours> entityHours = new HashMap<>();
        dtoHours.forEach((day, hours) ->
                entityHours.put(
                        day.toLowerCase(),
                        new VendorProfile.DayHours(hours.getIsOpen(), hours.getOpenTime(), hours.getCloseTime())
                )
        );
        return entityHours;
    }

    private Map<String, VendorProfile.DayHours> getDefaultOperatingHours() {
        Map<String, VendorProfile.DayHours> defaults = new HashMap<>();
        for (String day : new String[]{"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"}) {
            VendorProfile.DayHours hours = new VendorProfile.DayHours();
            hours.setIsOpen(!day.equals("sunday"));
            hours.setOpenTime("09:00");
            hours.setCloseTime("22:00");
            defaults.put(day, hours);
        }
        return defaults;
    }
}
