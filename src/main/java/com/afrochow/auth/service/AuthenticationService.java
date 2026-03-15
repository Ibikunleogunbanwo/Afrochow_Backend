package com.afrochow.auth.service;
import com.afrochow.address.model.Address;
import com.afrochow.admin.dto.AdminProfileRequestDto;
import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.admin.model.AdminProfile;
import com.afrochow.admin.repository.AdminProfileRepository;
import com.afrochow.auth.dto.BaseRegistrationRequest;
import com.afrochow.auth.dto.ForgotPasswordRequestDto;
import com.afrochow.auth.dto.LoginRequest;
import com.afrochow.auth.dto.ResetPasswordRequestDto;
import com.afrochow.customer.dto.CustomerProfileRequestDto;
import com.afrochow.email.EmailVerificationTokenRepository;
import com.afrochow.security.Utils.CookieConstants;
import com.afrochow.security.Utils.CookieUtils;
import com.afrochow.security.repository.PasswordResetTokenRepository;
import com.afrochow.common.exceptions.*;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.auth.dto.LoginResponse;
import com.afrochow.auth.dto.RegistrationResponse;
import com.afrochow.security.dto.TokenRefreshResponseDto;
import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.Role;
import com.afrochow.security.JwtTokenProvider;
import com.afrochow.security.Services.*;
import com.afrochow.security.Utils.SecurityUtils;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.security.model.EmailVerificationToken;
import com.afrochow.security.model.PasswordResetToken;
import com.afrochow.security.model.RefreshToken;
import com.afrochow.email.EmailService;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.user.dto.UserCustomerSummaryDto;
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
import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final AuthenticationManager authenticationManager;
    private final SecurityEventService securityEventService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final PasswordPolicyService passwordPolicyService;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;


    @Value("${security.password-reset.expiration-minutes:60}")
    private long passwordResetExpirationMinutes;
    @Value("${security.email-verification.expiration-minutes:1440}")
    private long emailVerificationExpirationMinutes;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /* ==========================================================
       Get Current User
       ========================================================== */


        public UserCustomerSummaryDto getCurrentUser(Authentication authentication) {

            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            if (!(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
                return null;
            }

            User user = principal.getUser();

            if (user == null) {
                return null;
            }

            return UserCustomerSummaryDto.builder()
                    .publicUserId(user.getPublicUserId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .phone(user.getPhone())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .isActive(user.getIsActive())
                    .build();
        }




    /* ==========================================================
       LOGIN
       ========================================================== */

        /**
         * Authenticate user and generate JWT tokens.
         * * Security features:
         * - Rate limiting per IP + identifier
         * - Account lockout protection
         * - Email verification requirement
         * - Failed attempt tracking
         *
         * @param loginRequest Login credentials
         * @param httpRequest  HTTP request for IP tracking
         * @return LoginResponse with tokens and user info
         * @throws BadCredentialsException   if credentials are invalid
         * @throws AccountLockedException    if an account is locked
         * @throws EmailNotVerifiedException if email isn't verified
         */
        @Transactional
        public LoginResponse login(
                LoginRequest loginRequest,
                HttpServletRequest httpRequest,
                HttpServletResponse httpResponse
        ) {
            String identifier = loginRequest.getIdentifier().trim();
            String password = loginRequest.getPassword().trim();
            String clientIp = SecurityUtils.getClientIP(httpRequest);

            // ---------- pre-authentication guards ----------
            rateLimitService.verifyLoginLimit(clientIp + ":" + identifier);
            validateAccountNotLocked(identifier, clientIp);

            try {
                // ---------- authenticate ----------
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(identifier, password)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // ---------- principal & user ----------
                CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
                if (principal == null || principal.getUser() == null) {
                    throw new IllegalStateException("Authenticated principal does not contain a User entity");
                }

                User user = principal.getUser();

                // ---------- post-auth business rules ----------
                if (!user.getEmailVerified()) {
                    log.warn("Login attempt with unverified email: {}", user.getEmail());
                    throw new EmailNotVerifiedException(
                            "Email not verified. Please check your email and verify your account before logging in.",
                            user.getEmail()
                    );
                }

                // ---------- refresh token rotation ----------
                String refreshToken = refreshTokenService.createRefreshTokenForUser(user, httpRequest);

                // ---------- set cookies ----------
                String accessToken = jwtTokenProvider.createToken(user);

                CookieUtils.addHttpOnlyCookie(
                        httpResponse,
                        CookieConstants.ACCESS_TOKEN_COOKIE,
                        accessToken,
                        jwtTokenProvider.getAccessTokenExpirationSeconds(),
                        true,
                        "Lax"
                );

                CookieUtils.addHttpOnlyCookie(
                        httpResponse,
                        CookieConstants.REFRESH_TOKEN_COOKIE,
                        refreshToken,
                        refreshTokenService.getRefreshTokenExpirationSeconds(),
                        true,
                        "Lax"
                );

                // ---------- logging & audit ----------
                loginAttemptService.loginSucceeded(user.getEmail(), httpRequest);
                securityEventService.logLoginSuccess(user.getEmail(), httpRequest);

                return buildLoginResponse(user);

            } catch (DisabledException de) {
                throw new DisabledAccountException("Please verify your e-mail.");
            } catch (LockedException le) {
                long remainingSeconds = loginAttemptService.getRemainingLockoutSeconds(identifier);
                throw new AccountLockedException("Account locked due to too many failed attempts.", remainingSeconds);
            } catch (BadCredentialsException bce) {
                loginAttemptService.loginFailed(identifier, httpRequest);
                int attemptCount = loginAttemptService.getAttemptCount(identifier);
                securityEventService.logFailedLoginAttempt(identifier, clientIp, attemptCount, httpRequest);
                throw new BadCredentialsException("Invalid username/email or password");
            }
        }


    /* ==========================================================
       TOKEN REFRESH
       ========================================================== */

        /**
         * Refresh JWT access token using refresh token.*
         * Security features:
         * - Token rotation (old token revoked, new token issued)
         * - IP tracking
         * - Token reuse detection
         *
         * @param httpResponse Token refresh request
         * @param httpRequest  HTTP request for audit logging
         * @return New access token and refresh token
         */
        @Transactional
        public TokenRefreshResponseDto refreshTokenFromCookie(
                String refreshToken,
                HttpServletRequest httpRequest,
                HttpServletResponse httpResponse
        ) {
            String clientIp = SecurityUtils.getClientIP(httpRequest);

            RefreshToken validToken = refreshTokenService.verifyRefreshToken(refreshToken);
            User user = validToken.getUser();

            String newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken, httpRequest);
            String newAccessToken = jwtTokenProvider.createToken(user);

            CookieUtils.addHttpOnlyCookie(
                    httpResponse,
                    CookieConstants.ACCESS_TOKEN_COOKIE,
                    newAccessToken,
                    jwtTokenProvider.getAccessTokenExpirationSeconds(),
                    true,
                    "Lax"
            );

            CookieUtils.addHttpOnlyCookie(
                    httpResponse,
                    CookieConstants.REFRESH_TOKEN_COOKIE,
                    newRefreshToken,
                    refreshTokenService.getRefreshTokenExpirationSeconds(),
                    true,
                    "Lax"
            );

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
         * Logout user by revoking refresh token.
         *
         * @param httpRequest Refresh token to revoke
         */
        @Transactional
        public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

            Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (CookieConstants.REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                        String refreshToken = cookie.getValue();
                        refreshTokenService.revokeToken(refreshToken);
                        log.info("Refresh token revoked for logout");
                    }
                }
            }

            CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.ACCESS_TOKEN_COOKIE, "", 0, true, "Lax");
            CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE, "", 0, true, "Lax");
            log.info("User logged out (cookies cleared)");
        }


        /**
         * Logout user from all devices by revoking all refresh tokens.
         * Uses SecurityUtils to get the current authenticated user.
         *
         * @throws UnauthorizedException if not authenticated
         */
        @Transactional
        public void logoutAllDevices(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
            String username = SecurityUtils.getCurrentUsername();
            if (username == null) throw new UnauthorizedException("Not authenticated");

            String clientIp = SecurityUtils.getClientIP(httpRequest);

            // Revoke all tokens first
            refreshTokenService.revokeAllUserTokens(username);
            log.info("User {} logged out from all devices (IP: {})", username, clientIp);

            // Clear current device cookies
            CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.ACCESS_TOKEN_COOKIE, "", 0, true, "Lax");
            CookieUtils.addHttpOnlyCookie(httpResponse, CookieConstants.REFRESH_TOKEN_COOKIE, "", 0, true, "Lax");
            log.info("Current device cookies cleared (IP: {})", clientIp);
        }


    /* ==========================================================
       REGISTRATION
       ========================================================== */

        /**
         * Register a new customer account.
         *
         * @param request     Customer registration data
         * @param httpRequest HTTP request for IP tracking
         * @return Registration response with email verification instructions
         */
        @Transactional
        public RegistrationResponse registerCustomer(CustomerProfileRequestDto request, HttpServletRequest httpRequest) {
            String clientIp = SecurityUtils.getClientIP(httpRequest);

            // Rate limit and validate registration data
            rateLimitService.verifyRegistrationLimit(clientIp);
            validateRegistrationData(request.getEmail(), request.getUsername(), request.getPhone());

            // Create a User object
            User user = createUser(request, Role.CUSTOMER);

            // Save the user (aggregate root)
            User savedUser = userRepository.save(user);

            // Create CustomerProfile + Address
            createCustomerProfile(savedUser, request);

            // Create and send verification email now
            createAndSendEmailVerificationToken(savedUser);

            // Log and notify
            securityEventService.logRegistration(savedUser.getEmail(), httpRequest);

            return registrationResponse(savedUser);
        }


        /**
         * Register a new vendor/restaurant account.
         *
         * @param request     Vendor registration data
         * @param httpRequest HTTP request for IP tracking
         * @return Registration response with email verification instructions
         */
        @Transactional
        public RegistrationResponse registerVendor(VendorProfileRequestDto request, HttpServletRequest httpRequest) {
            String clientIp = SecurityUtils.getClientIP(httpRequest);

            // Rate limit and validate registration data
            rateLimitService.verifyRegistrationLimit(clientIp);
            validateRegistrationData(request.getEmail(), request.getUsername(), request.getPhone());

            // Create user and profile
            User user = createUser(request, Role.VENDOR);

            User savedUser = userRepository.save(user);

            createVendorProfile(savedUser, request);

            // Send verification email
            createAndSendEmailVerificationToken(savedUser);

            // Log and notify - use savedUser consistently
            securityEventService.logRegistration(savedUser.getEmail(), httpRequest);

            return registrationResponse(savedUser);
        }


        /**
         * Register a new admin account*
         * SECURED: Only SUPER_ADMIN can create admin accounts.
         * Use SecurityUtils to get current authenticated admin.
         *
         * @param request     Admin registration data
         * @param httpRequest HTTP request for IP tracking
         * @return LoginResponse with JWT tokens (no email verification required for admins)
         * @throws UnauthorizedException           if not authenticated or user not found
         * @throws InsufficientPermissionException if not SUPER_ADMIN
         */
        @Transactional
        public AdminProfileResponseDto registerAdmin(AdminProfileRequestDto request, HttpServletRequest httpRequest) {
            Long requestingAdminId = SecurityUtils.getCurrentUserId();

            if (requestingAdminId == null) {
                throw new UnauthorizedException("Authentication required to create admin accounts");
            }
            User requestingAdmin = userRepository.findById(requestingAdminId)
                    .orElseThrow(() -> new UnauthorizedException("Admin user not found"));

            if (requestingAdmin.getRole() != Role.ADMIN || requestingAdmin.getAdminProfile() == null) {
                throw new InsufficientPermissionException("Only admins can create other admin accounts");
            }

            // Verify SUPER_ADMIN access level
            if (requestingAdmin.getAdminProfile().getAccessLevel() != AdminAccessLevel.SUPER_ADMIN) {
                throw new InsufficientPermissionException("Only SUPER_ADMIN can create admin accounts");
            }

            // Validate and create new admin
            validateRegistrationData(request.getEmail(), request.getUsername(), request.getPhone());
            User newAdmin = createUser(request, Role.ADMIN);

            // Save user FIRST to get database ID
            User savedUser = userRepository.save(newAdmin);

            // Create profile with SAVED user
            createAdminProfile(savedUser, request);

            // Log admin creation
            log.info("Admin account created by SUPER_ADMIN: {} (userId: {})",
                    requestingAdmin.getEmail(), requestingAdminId);

            // Use savedUser consistently throughout
            securityEventService.logRegistration(savedUser.getEmail(), httpRequest);

            // Return login response (admins don't need email verification)
            return adminProfileResponseDto(savedUser);
        }



    /* ==========================================================
       PASSWORD RESET
       ========================================================== */

        /**
         * Request password reset (send reset email)*
         * Security: Returns generic message to prevent email enumeration.
         * Rate limits per canonical identifier.
         *
         * @param request     Forgot password request
         * @param httpRequest HTTP request for IP tracking
         * @return Generic success message
         */
        @Transactional
        public String forgotPassword(ForgotPasswordRequestDto request, HttpServletRequest httpRequest) {
            String identifier = request.getIdentifier().trim();

            // Rate limit per canonical email (even if an account doesn't exist)
            rateLimitService.verifyPasswordResetLimit(getCanonicalIdentifier(identifier));

            // Process reset request if user exists
            Optional<User> userOpt = findUserByIdentifier(identifier);
            userOpt.ifPresent(user -> processPasswordResetRequest(user, httpRequest));

            // Generic message to prevent user enumeration
            return "If the username or email exists in our system, you will receive reset instructions.";
        }


        @Transactional
        public void resetPassword(ResetPasswordRequestDto request, HttpServletRequest httpRequest) {
            if (!StringUtils.hasText(request.getToken()) || !StringUtils.hasText(request.getNewPassword())) {
                throw new IllegalArgumentException("Token and new password must be provided");
            }

            // Find token by hashed match
            PasswordResetToken tokenEntity = passwordResetTokenRepository.findAll().stream()
                    .filter(t -> passwordEncoder.matches(request.getToken(), t.getTokenHash()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

            // Check if token is valid
            if (tokenEntity.isUsed() || tokenEntity.isExpired()) {
                throw new InvalidTokenException("Token expired or already used");
            }

            // Reset user password
            User user = tokenEntity.getUser();
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));

            // Mark token as used
            tokenEntity.markAsUsed();

            passwordResetTokenRepository.save(tokenEntity);

            // Log the reset event
            securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);
        }



    @Transactional
    public void changePassword(String publicUserId, String newPassword, HttpServletRequest httpRequest) {

        // 1️⃣ Rate-limit password change attempts (per user)
        rateLimitService.verifyPasswordResetLimit(
                getCanonicalIdentifier(publicUserId)
        );

        // 2️⃣ Validate new password
        if (!StringUtils.hasText(newPassword)) {
            throw new IllegalArgumentException("New password must be provided");
        }

        // 3️⃣ Fetch authenticated user
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // 4️⃣ Prevent re-using the same password (recommended)
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        // 5️⃣ Encode & update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 6️⃣ Security audit logging
        securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);

        // 7️⃣ Send password changed notification (in-app + email)
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstName());

        notificationService.createNotification(
            user.getPublicUserId(),
            "Password Changed Successfully",
            "Your password has been changed successfully. If you did not make this change, please contact support immediately.",
            com.afrochow.common.enums.NotificationType.SYSTEM_ALERT,
            null,
            null
        );
    }





    /* ==========================================================
        EMAIL VERIFICATION
       ========================================================== */

        /**
         * Verify email using verification token.
         *
         * @param token Email verification token
         * @return Success message
         * @throws BadCredentialsException if the token is invalid or expired
         */
        @Transactional
        public String verifyEmail(String token) {
            // Find and validate token
            EmailVerificationToken verificationToken = emailVerificationTokenRepository
                    .findValidToken(token, Instant.now())
                    .orElseThrow(() -> new BadCredentialsException("Invalid or expired verification token"));

            // Mark email as verified
            User user = verificationToken.getUser();
            user.setEmailVerified(true);

            if (user.getRole() == Role.CUSTOMER) {
                emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName(), Role.CUSTOMER.name());
            }

            if (user.getRole() == Role.VENDOR) {
                emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName(), Role.VENDOR.name());
            }

            if (user.getRole() == Role.ADMIN) {
                emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName(), Role.ADMIN.name());
            }

            // Send welcome in-app notification
            String welcomeMessage = user.getRole() == Role.VENDOR
                ? "Welcome to Afrochow! Your vendor account is now active. You can start adding your products and manage orders."
                : user.getRole() == Role.ADMIN
                ? "Welcome to Afrochow Admin! Your admin account is now active."
                : "Welcome to Afrochow! Your account is now verified. Explore our amazing African cuisine!";

            notificationService.createNotification(
                user.getPublicUserId(),
                "Welcome to Afrochow! 🎉",
                welcomeMessage,
                com.afrochow.common.enums.NotificationType.SYSTEM_ALERT,
                null,
                null
            );

            // Mark token as used
            verificationToken.markAsUsed();

            // Save changes
            emailVerificationTokenRepository.save(verificationToken);
            userRepository.save(user);

            log.info("Email verified for user: {}", user.getPublicUserId());
            return "Email verified successfully. You can now login to your account.";
        }


        /**
         * Resend an email verification link.
         *
         * @param email User email address
         * @throws ResourceNotFoundException if user not found
         */
        @Transactional
        public void resendVerificationEmail(String email) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

            // Check if already verified
            if (user.getEmailVerified()) {
                return;
            }

            // Revoke old tokens and send new one
            emailVerificationTokenRepository.revokeAllUserTokens(user.getUserId());
            createAndSendEmailVerificationToken(user);

        }

    /* ==========================================================
       PRIVATE HELPER METHODS
       ========================================================== */


        /**
         * Validate that account is not locked due to failed login attempts.
         */
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


        /**
         * Validate registration data (email, username, phone uniqueness).
         */
        private void validateRegistrationData(String email, String username, String phone) {
            if (userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException("Email already Exist");
            }

            if (phone != null && !phone.trim().isEmpty() && userRepository.existsByPhone(phone)) {
                throw new PhoneNumberAlreadyExistsException("Phone number already registered");
            }

            if (username != null && !username.trim().isEmpty() && userRepository.existsByUsername(username)) {
                throw new UserNameAlreadyExistsException("Username already exists");
            }
        }


        /**
         * Create base User entity from registration request.
         */
        private User createUser(BaseRegistrationRequest request, Role role) {
            passwordPolicyService.validatePassword(request.getPassword());

            return User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phone(request.getPhone())
                    .role(role)
                    .profileImageUrl(request.getProfileImageUrl())
                    .isActive(true)
                    .acceptTerms(request.getAcceptTerms())
                    .emailVerified(false)
                    .build();
        }


        private void createCustomerProfile(User user, CustomerProfileRequestDto request) {

            // Build CustomerProfile
            CustomerProfile customerProfile = CustomerProfile.builder()
                    .defaultDeliveryInstructions(request.getDefaultDeliveryInstructions())
                    .build();

            // Link owning side
            customerProfile.setUser(user);

            // Link inverse side
            user.setCustomerProfile(customerProfile);

            // Build and add Address(es)
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


        /**
         * Create a vendor profile with restaurant details and address.
         */
        @Transactional
        public void createVendorProfile(User user, VendorProfileRequestDto request) {

            if (user.getVendorProfile() != null) {
                throw new IllegalStateException("User already has a vendor profile");
            }

            if (request.getAddress() == null) {
                throw new IllegalArgumentException("Address is required");
            }

            // Create address (will be saved via cascade)
            Address address = Address.builder()
                    .addressLine(request.getAddress().getAddressLine())
                    .city(request.getAddress().getCity())
                    .province(request.getAddress().getProvince())
                    .postalCode(request.getAddress().getPostalCode())
                    .country(request.getAddress().getCountry())
                    .defaultAddress(request.getAddress().getDefaultAddress())
                    .build();

            String timezone = VendorProfile.getTimezoneFromProvince(request.getAddress().getProvince());

            // Convert operating hours
            Map<String, VendorProfile.DayHours> operatingHours =
                    convertOperatingHours(request.getOperatingHours());
            operatingHours.replaceAll((k, v) -> Map.entry(k.toLowerCase(), v).getValue());

            // Optional: validate time ranges per day
            operatingHours.values().forEach(d -> {
                if (Boolean.TRUE.equals(d.getIsOpen())) {
                    LocalTime open = LocalTime.parse(d.getOpenTime());
                    LocalTime close = LocalTime.parse(d.getCloseTime());
                    if (!close.isAfter(open)) {
                        throw new IllegalArgumentException("Closing time must be after opening time for all open days");
                    }
                }
            });

            // Build vendor profile
            VendorProfile vendorProfile = VendorProfile.builder()
                    .user(user)
                    .restaurantName(request.getRestaurantName())
                    .description(request.getDescription())
                    .cuisineType(request.getCuisineType())
                    .logoUrl(request.getLogoUrl())
                    .bannerUrl(request.getBannerUrl())
                    .businessLicenseUrl(request.getBusinessLicenseUrl())
                    .taxId(request.getTaxId())
                    .isVerified(false)
                    .isActive(true)
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

            // Sync bidirectional relationship
            user.setVendorProfile(vendorProfile);

            vendorProfileRepository.save(vendorProfile);
        }


    /**
         * Create admin profile with permissions.
         */
        private void createAdminProfile(User user, AdminProfileRequestDto request) {
            AdminProfile adminProfile = AdminProfile.builder()
                    .user(user)
                    .department(request.getDepartment())
                    .accessLevel(request.getAccessLevel() != null ? request.getAccessLevel() : AdminAccessLevel.MODERATOR)
                    .employeeId(request.getEmployeeId())
                    .canVerifyVendors(request.getCanVerifyVendors() != null ? request.getCanVerifyVendors() : false)
                    .canManageUsers(request.getCanManageUsers() != null ? request.getCanManageUsers() : false)
                    .canViewReports(request.getCanViewReports() != null ? request.getCanViewReports() : false)
                    .canManagePayments(request.getCanManagePayments() != null ? request.getCanManagePayments() : false)
                    .canManageCategories(request.getCanManageCategories() != null ? request.getCanManageCategories() : false)
                    .canResolveDisputes(request.getCanResolveDisputes() != null ? request.getCanResolveDisputes() : false)
                    .build();

            user.setAdminProfile(adminProfile);
            adminProfileRepository.save(adminProfile);
            userRepository.save(user);
        }


        /**
         * Process password reset request by creating token and sending email.
         */
        @Transactional
        public void processPasswordResetRequest(User user, HttpServletRequest httpRequest) {
            passwordResetTokenRepository.revokeAllUserTokens(user.getUserId());

            PasswordResetToken token = PasswordResetToken.create(
                    user,
                    passwordResetExpirationMinutes,
                    SecurityUtils.getClientIP(httpRequest),
                    httpRequest.getHeader("User-Agent"),
                    passwordEncoder
            );

            passwordResetTokenRepository.save(token);

            String resetLink = frontendUrl + "/reset-password?token=" + token.getTransientRawToken();

            securityEventService.logPasswordResetRequest(user.getEmail(), httpRequest);

            // Send email and in-app notification for password reset request
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), resetLink);

            notificationService.createNotification(
                user.getPublicUserId(),
                "Password Reset Requested",
                "A password reset was requested for your account. If you did not request this, please secure your account immediately.",
                com.afrochow.common.enums.NotificationType.SYSTEM_ALERT,
                null,
                null
            );
        }


        /**
         * Build login response and set auth cookies.
         */
        private LoginResponse buildLoginResponse(User user) {
            return LoginResponse.builder()
                    .publicUserId(user.getPublicUserId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .build();
        }

    /**
     * Build admin response and set auth cookies.
     */
    private AdminProfileResponseDto adminProfileResponseDto(User user) {
        AdminProfile profile = user.getAdminProfile();

        return AdminProfileResponseDto.builder()
                .publicUserId(user.getPublicUserId())
                .department(profile.getDepartment())
                .accessLevel(profile.getAccessLevel())
                .username(user.getUsername())
                .employeeId(profile.getEmployeeId())
                .isSuperAdmin(profile.getAccessLevel() == AdminAccessLevel.SUPER_ADMIN)
                .hasFullAccess(profile.getAccessLevel() == AdminAccessLevel.SUPER_ADMIN)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }



    /**
         * Build registration response with email verification instructions.
         */
        private RegistrationResponse registrationResponse(User user) {
            return RegistrationResponse.builder()
                    .message("Registration successful!")
                    .email(user.getEmail())
                    .publicUserId(user.getPublicUserId())
                    .emailVerified(false)
                    .nextStep("Please check your email to verify your account before logging in.")
                    .build();
        }




        /**
         * Find user by identifier (email, username, or publicUserId).
         */
        private Optional<User> findUserByIdentifier(String identifier) {
            if (identifier.contains("@")) {
                return userRepository.findByEmail(identifier);
            }
            return userRepository.findByUsername(identifier);

        }


        /**
         * Convert identifier to canonical form for rate limiting.
         * Emails are lowercased, other identifiers unchanged.
         */
        private String getCanonicalIdentifier(String identifier) {
            return identifier.contains("@") ? identifier.toLowerCase() : identifier;
        }


        /**
         * Create and send email verification token.
         * Revokes any existing tokens first for safety.
         */
        private void createAndSendEmailVerificationToken(User user) {
            // Revoke any existing verification tokens (safety measure)
            emailVerificationTokenRepository.revokeAllUserTokens(user.getUserId());

            // Create new verification token
            EmailVerificationToken verificationToken = EmailVerificationToken.create(
                    user,
                    emailVerificationExpirationMinutes
            );
            emailVerificationTokenRepository.save(verificationToken);

            // Send verification email
            emailService.sendEmailVerificationEmail(
                    user.getEmail(),
                    verificationToken.getToken(),
                    user.getFirstName()
            );

            log.info("Email verification token created and sent to: {}", user.getPublicUserId());
        }

        /**
         * Convert DTO operating hours to entity format
         */
        private Map<String, VendorProfile.DayHours> convertOperatingHours(
                Map<String, VendorProfileRequestDto.DayHoursDto> dtoHours) {

            Map<String, VendorProfile.DayHours> entityHours = new HashMap<>();

            if (dtoHours == null) {
                return getDefaultOperatingHours();
            }

            dtoHours.forEach((day, hours) -> {
                VendorProfile.DayHours dayHours = new VendorProfile.DayHours(
                        hours.getIsOpen(),
                        hours.getOpenTime(),
                        hours.getCloseTime()
                );
                entityHours.put(day.toLowerCase(), dayHours);
            });

            return entityHours;
        }


        /**
         * Get default operating hours (Mon-Sat open, Sunday closed)
         */
        private Map<String, VendorProfile.DayHours> getDefaultOperatingHours() {
            Map<String, VendorProfile.DayHours> defaultHours = new HashMap<>();
            String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};

            for (String day : days) {
                VendorProfile.DayHours hours = new VendorProfile.DayHours();
                hours.setIsOpen(!day.equals("sunday"));
                hours.setOpenTime("09:00");
                hours.setCloseTime("22:00");
                defaultHours.put(day, hours);
            }

            return defaultHours;
        }


        private void validateVendorRequest(VendorProfileRequestDto request) {
            if (request.getDeliveryFee() != null &&
                    request.getDeliveryFee().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Delivery fee cannot be negative");
            }

            if (!request.getOffersDelivery() && !request.getOffersPickup()) {
                throw new IllegalArgumentException(
                        "Vendor must offer at least delivery or pickup");
            }

            // Validate operating hours exist and at least one day is open
            if (convertOperatingHours(request.getOperatingHours()).values().stream()
                    .noneMatch(VendorProfile.DayHours::getIsOpen)) {
                throw new IllegalArgumentException(
                        "Vendor must be open at least one day per week");
            }


        }
    }