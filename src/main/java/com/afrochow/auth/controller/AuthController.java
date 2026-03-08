package com.afrochow.auth.controller;
import com.afrochow.admin.dto.AdminProfileRequestDto;
import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.auth.dto.*;
import com.afrochow.customer.dto.CustomerProfileRequestDto;
import com.afrochow.security.Utils.CookieConstants;
import com.afrochow.security.dto.TokenRefreshResponseDto;
import com.afrochow.auth.service.AuthenticationService;
import com.afrochow.common.ApiResponse;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.dto.UserCustomerSummaryDto;
import com.afrochow.user.model.User;
import com.afrochow.vendor.dto.VendorProfileRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication and user registration.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>User registration (customer, vendor, admin)</li>
 *   <li>Login and logout operations</li>
 *   <li>Token management (refresh, revoke)</li>
 *   <li>Password reset flow</li>
 *   <li>Email verification</li>
 * </ul>
 *
 * <p>All endpoints include proper security controls:
 * <ul>
 *   <li>Rate limiting on sensitive operations</li>
 *   <li>Account lockout protection</li>
 *   <li>IP tracking and audit logging</li>
 *   <li>Token rotation on refresh</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and token management endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;

    /* ==========================================================
       REGISTRATION ENDPOINTS
       ========================================================== */

    /**
     * Register a new customer account.
     *
     * <p>Creates a customer user with profile and default address.
     * Email verification is required before login.
     *
     * @param request Customer registration data
     * @param httpRequest HTTP request for IP tracking
     * @return Registration confirmation with verification instructions
     */
    @PostMapping("/register/customer")
    @Operation(summary = "Register Customer", description = "Register a new customer account. Email verification required before login.")
    public ResponseEntity<ApiResponse<RegistrationResponse>> registerCustomer(
            @Valid @RequestBody CustomerProfileRequestDto request,
            HttpServletRequest httpRequest
    ) {
        RegistrationResponse userResponse = authenticationService.registerCustomer(request, httpRequest);

        return ResponseEntity.ok(
                ApiResponse.success("Customer registered successfully. Please check your email to verify your account.", userResponse)
        );
    }


    /**
     * Register a new vendor/restaurant account.
     *
     * <p>Creates a vendor user with a restaurant profile and business address.
     * Email verification is required before login.
     *
     * @param request Vendor registration data (includes restaurant details)
     * @param httpRequest HTTP request for IP tracking
     * @return Registration confirmation with verification instructions
     */
    @PostMapping("/register/vendor")
    @Operation(
            summary = "Register Vendor",
            description = "Register a new vendor/restaurant account. Address is required. Email verification required before login."
    )
    public ResponseEntity<ApiResponse<RegistrationResponse>> registerVendor(
            @Valid @RequestBody VendorProfileRequestDto request,
            HttpServletRequest httpRequest
    ) {
        RegistrationResponse vendorResponse = authenticationService.registerVendor(request, httpRequest);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Vendor registered successfully. Please check your email to verify your account.",
                        vendorResponse
                )
        );
    }





    /**
     * Register a new admin account.
     *
     * <p><b>SECURED:</b> Requires an ADMIN role with SUPER_ADMIN access level.
     * Only SUPER_ADMIN users can create new admin accounts.
     * No email verification required - returns JWT tokens immediately.
     *
     * @param request Admin registration data (includes permissions)
     * @param httpRequest HTTP request for IP tracking
     * @return JWT tokens and user info (no email verification needed)
     */
    @PostMapping("/register/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Register Admin",
            description = "Register a new admin account. Requires SUPER_ADMIN privileges. Returns JWT tokens immediately."
    )
    public ResponseEntity<ApiResponse<AdminProfileResponseDto>> registerAdmin(
            @Valid @RequestBody AdminProfileRequestDto request,
            HttpServletRequest httpRequest
    ) {
        AdminProfileResponseDto response = authenticationService.registerAdmin(request, httpRequest);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Admin registered successfully",
                        response
                )
        );
    }

    /* ==========================================================
       AUTHENTICATION ENDPOINTS
       ========================================================== */

    /**
     * Login endpoint with comprehensive security features.
     *
     * <p>Security Features:
     * <ul>
     *   <li>Account lockout after failed attempts (configurable)</li>
     *   <li>Rate limiting per IP and identifier</li>
     *   <li>IP and User-Agent tracking</li>
     *   <li>Failed attempt counter and logging</li>
     *   <li>Generic error messages to prevent user enumeration</li>
     *   <li>Email verification check</li>
     * </ul>
     *
     * @param loginRequest Login credentials (email/username and password)
     * @param httpRequest HTTP request for IP/User-Agent tracking
     * @return JWT access token, refresh token, and user info
     */
    @PostMapping("/login")
    @Operation(
            summary = "Login",
            description = "Authenticate user with email/username and password"
    )
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        LoginResponse user = authenticationService.login(
                loginRequest,
                httpRequest,
                httpResponse
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Login successful. JWT tokens issued.",
                        user
                )
        );
    }


    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserCustomerSummaryDto>> me(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.unauthorized("Unauthenticated"));
        }

        UserCustomerSummaryDto user =
                authenticationService.getCurrentUser(authentication);

        return ResponseEntity.ok(ApiResponse.success(user));
    }





    /**
     * Logout by revoking the provided refresh token.
     *
     * <p>This logs the user out from the current device only.
     * For logging out from all devices, use the /logout-all endpoint.
     *
     * @param httpRequest Token refresh request containing refresh token to revoke
     * @return Success message
     */
    @PostMapping("/logout")
    @Operation(
            summary = "Logout",
            description = "Revoke refresh token to logout user from current device"
    )
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        authenticationService.logout(httpRequest, httpResponse);

        return ResponseEntity.ok(
                ApiResponse.success("Logged out successfully")
        );
    }


    /**
     * Logout from all devices by revoking all refresh tokens.
     *
     * <p><b>SECURED:</b> Requires authentication.
     * Users can only log out their own devices.
     * @return Success message
     */
    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Logout from all devices",
            description = "Revoke all refresh tokens for the authenticated user"
    )
    public ResponseEntity<ApiResponse<Void>> logoutAllDevices(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        authenticationService.logoutAllDevices(httpRequest, httpResponse);
        return ResponseEntity.ok(
                ApiResponse.success("Logged out from all devices successfully")
        );
    }



    /* ==========================================================
       TOKEN MANAGEMENT ENDPOINTS
       ========================================================== */

    /**
     * Refresh JWT access token using refresh token.
     *
     * <p>Security Features:
     * <ul>
     *   <li>Token rotation (old token revoked, new token issued)</li>
     *   <li>IP and User-Agent tracking</li>
     *   <li>Token reuse detection</li>
     * </ul>
     *
     * @param httpResponse Token refresh request containing refresh token
     * @param httpRequest HTTP request for audit logging
     * @return New access token and new refresh token
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Get new access token using refresh token stored in HttpOnly cookie. Old refresh token will be rotated."
    )
    public ResponseEntity<ApiResponse<TokenRefreshResponseDto>> refreshToken(
            @CookieValue(name = CookieConstants.REFRESH_TOKEN_COOKIE, required = true) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        TokenRefreshResponseDto response = authenticationService.refreshTokenFromCookie(refreshToken, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }



    /* ==========================================================
       PASSWORD RESET ENDPOINTS
       ========================================================== */

    /**
     * Request password reset email.
     *
     * <p><b>Security:</b> Returns a generic message to prevent email enumeration.
     * Rate limited per identifier to prevent abuse.
     *
     * @param request Forgot password request (contains email/username)
     * @param httpRequest HTTP request for IP tracking
     * @return Generic success message (doesn't reveal if an account exists)
     */
    @PostMapping("/forgot-password")
    @Operation(
            summary = "Forgot password",
            description = "Request password reset. An email will be sent if the address exists."
    )
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request,
            HttpServletRequest httpRequest
    ) {
        String message = authenticationService.forgotPassword(request, httpRequest);

        return ResponseEntity.ok(ApiResponse.success(message));
    }


    /**
     * Reset the password using token received via email.
     *
     * <p>Validates the reset token, updates the password, and revokes all
     * existing sessions (forcing re-login on all devices).
     *
     * @param request Reset password request (token and new password)
     * @param httpRequest HTTP request for IP tracking
     * @return Success message
     */
    @PostMapping("/reset-password")
    @Operation(
            summary = "Reset password",
            description = "Reset password using the token received via email"
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto request,
            HttpServletRequest httpRequest
    ) {
        authenticationService.resetPassword(request, httpRequest);

        return ResponseEntity.ok(
                ApiResponse.success("Password has been reset successfully.")
        );
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Change password",
            description = "Change password for the authenticated user"
    )
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody NewPasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest
    ) {

        String publicUserId = userDetails.getPublicUserId();


        authenticationService.changePassword(
                publicUserId,
                request.newPassword(),
                httpRequest
        );

        return ResponseEntity.ok(
                ApiResponse.success("Password has been changed successfully.")
        );
    }


    /* ==========================================================
       EMAIL VERIFICATION ENDPOINTS
       ========================================================== */

    /**
     * Verify email address using token received via email.
     *
     * <p>Once verified, the user can log in to their account.
     *
     * @param dto Email verification token
     * @return Success message
     */
    @PostMapping("/verify-email")
    @Operation(
            summary = "Verify email",
            description = "Verify email address using the token received via email"
    )
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailDto dto) {
        String message = authenticationService.verifyEmail(dto.getCode());
        return ResponseEntity.ok(
                ApiResponse.success(message)
        );
    }


    /**
     * Resend email verification link.
     *
     * <p>Useful if the original verification email was not received
     * or the token expired.
     *
     * @param dto User email address
     * @return Success message
     */
    @PostMapping("/resend-verification")
    @Operation(
            summary = "Resend verification email",
            description = "Send a new email verification link to the user's email address"
    )
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(@Valid @RequestBody ResendVerificationDto dto) {
        authenticationService.resendVerificationEmail(dto.getEmail());
        return ResponseEntity.ok(
                ApiResponse.success("Verification email sent successfully")
        );
    }

}