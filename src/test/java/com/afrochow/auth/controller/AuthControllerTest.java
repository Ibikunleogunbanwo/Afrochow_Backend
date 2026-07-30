package com.afrochow.auth.controller;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.auth.dto.*;
import com.afrochow.auth.service.AuthenticationService;
import com.afrochow.auth.service.GoogleAuthService;
import com.afrochow.common.enums.Province;
import com.afrochow.customer.dto.CustomerProfileRequestDto;
import com.afrochow.security.dto.TokenRefreshResponseDto;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.dto.UserCustomerSummaryDto;
import com.afrochow.user.model.User;
import com.afrochow.vendor.dto.VendorProfileRequestDto;
import com.afrochow.admin.dto.AdminProfileRequestDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for AuthController.
 *
 * All business logic (lockout, rate limiting, password hashing, token
 * issuance) lives in AuthenticationService/GoogleAuthService, both mocked
 * here — this covers routing, @Valid validation, and response shape via
 * GlobalExceptionHandler only. /me uses a plain {@code Authentication}
 * parameter (authenticatedAs), /logout-all uses
 * {@code @AuthenticationPrincipal CustomUserDetails} (authenticatedAsPrincipal).
 * registerVendor/registerAdmin success paths aren't covered — their request
 * DTOs pull in large nested object graphs (business profile, address, admin
 * permissions) that add little beyond what registerCustomer's success case
 * and each endpoint's validation-failure case already prove about the
 * shared BaseRegistrationRequest wiring.
 */
@ControllerSliceTest(AuthController.class)
class AuthControllerTest extends AbstractControllerTest {

    @MockitoBean private AuthenticationService authenticationService;
    @MockitoBean private GoogleAuthService googleAuthService;

    private static final String USERNAME = "ada";

    private CustomerProfileRequestDto validCustomerRequest() {
        CustomerProfileRequestDto dto = CustomerProfileRequestDto.builder()
                .username("ada")
                .defaultDeliveryInstructions("Leave at door")
                .address(AddressRequestDto.builder()
                        .addressLine("123 Main St")
                        .city("Calgary")
                        .province(Province.AB)
                        .postalCode("T2P1J9")
                        .build())
                .build();
        dto.setEmail("ada@afrochow.com");
        dto.setPassword("Passw0rd!");
        dto.setConfirmPassword("Passw0rd!");
        dto.setFirstName("Ada");
        dto.setLastName("Customer");
        dto.setPhone("4161234567");
        dto.setAcceptTerms(true);
        return dto;
    }

    @Test
    void googleLogin_returns200() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setCode("google-auth-code");

        LoginResponse response = LoginResponse.builder()
                .publicUserId("user-1").username(USERNAME).email("ada@afrochow.com").role("CUSTOMER").build();
        when(googleAuthService.authenticateWithGoogle(eq("google-auth-code"), isNull(),
                any(HttpServletRequest.class), any(HttpServletResponse.class))).thenReturn(response);

        mockMvc.perform(post("/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value("user-1"));
    }

    @Test
    void registerCustomer_valid_returns200() throws Exception {
        RegistrationResponse response = RegistrationResponse.builder()
                .message("Registered").email("ada@afrochow.com").publicUserId("user-1")
                .emailVerified(false).nextStep("VERIFY_EMAIL").build();
        when(authenticationService.registerCustomer(any(CustomerProfileRequestDto.class), any(HttpServletRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/auth/register/customer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCustomerRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value("user-1"));
    }

    @Test
    void registerCustomer_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/auth/register/customer")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authenticationService, never()).registerCustomer(any(), any());
    }

    @Test
    void registerVendor_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/auth/register/vendor")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authenticationService, never()).registerVendor(any(VendorProfileRequestDto.class), any());
    }

    @Test
    void registerAdmin_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/auth/register/admin")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authenticationService, never()).registerAdmin(any(AdminProfileRequestDto.class), any());
    }

    @Test
    void login_returns200() throws Exception {
        LoginRequest request = LoginRequest.builder().identifier("ada@afrochow.com").password("Passw0rd!").build();
        LoginResponse response = LoginResponse.builder()
                .publicUserId("user-1").username(USERNAME).email("ada@afrochow.com").role("CUSTOMER").build();
        when(authenticationService.login(any(LoginRequest.class), any(HttpServletRequest.class), any(HttpServletResponse.class)))
                .thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value("user-1"));
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authenticationService, never()).login(any(), any(), any());
    }

    @Test
    void me_authenticated_returns200() throws Exception {
        UserCustomerSummaryDto summary = UserCustomerSummaryDto.builder()
                .publicUserId("user-1").username(USERNAME).email("ada@afrochow.com").role("CUSTOMER").build();
        when(authenticationService.getCurrentUser(any())).thenReturn(summary);

        mockMvc.perform(get("/auth/me").with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value("user-1"));
    }

    @Test
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(authenticationService, never()).getCurrentUser(any());
    }

    @Test
    void logout_returns200() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authenticationService).logout(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void logoutAllDevices_returns200() throws Exception {
        LogoutAllRequestDto request = LogoutAllRequestDto.builder().password("Passw0rd!").build();

        mockMvc.perform(post("/auth/logout-all")
                        .with(authenticatedAsPrincipal(User.builder().publicUserId("user-1").build(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out from all devices successfully"));

        verify(authenticationService).logoutAllDevicesWithPasswordCheck(
                eq("user-1"), eq("Passw0rd!"), any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void refreshToken_returns200() throws Exception {
        TokenRefreshResponseDto response = TokenRefreshResponseDto.builder()
                .publicUserId("user-1").username(USERNAME).email("ada@afrochow.com").role("CUSTOMER").build();
        when(authenticationService.refreshTokenFromCookie(eq("some-refresh-token"),
                any(HttpServletRequest.class), any(HttpServletResponse.class))).thenReturn(response);

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value("user-1"));
    }

    @Test
    void refreshToken_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(authenticationService, never()).refreshTokenFromCookie(any(), any(), any());
    }

    @Test
    void forgotPassword_returns200() throws Exception {
        ForgotPasswordRequestDto request = ForgotPasswordRequestDto.builder().identifier("ada@afrochow.com").build();
        when(authenticationService.forgotPassword(any(ForgotPasswordRequestDto.class), any(HttpServletRequest.class)))
                .thenReturn("If an account exists, a reset email has been sent.");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists, a reset email has been sent."));
    }

    @Test
    void resetPassword_returns200() throws Exception {
        ResetPasswordRequestDto request = ResetPasswordRequestDto.builder()
                .token("reset-token").newPassword("NewPassw0rd!").build();

        mockMvc.perform(post("/auth/reset-password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully."));

        verify(authenticationService).resetPassword(any(ResetPasswordRequestDto.class), any(HttpServletRequest.class));
    }

    @Test
    void changePassword_returns200() throws Exception {
        ChangePasswordRequestDto request = ChangePasswordRequestDto.builder()
                .currentPassword("OldPassw0rd!").newPassword("NewPassw0rd!").confirmPassword("NewPassw0rd!").build();

        mockMvc.perform(post("/auth/change-password")
                        .with(authenticatedAsPrincipal(User.builder().publicUserId("user-1").build(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been changed successfully."));

        verify(authenticationService).changePassword(
                eq("user-1"), eq("OldPassw0rd!"), eq("NewPassw0rd!"), any(HttpServletRequest.class));
    }

    @Test
    void changePassword_mismatchedConfirm_returns400() throws Exception {
        ChangePasswordRequestDto request = ChangePasswordRequestDto.builder()
                .currentPassword("OldPassw0rd!").newPassword("NewPassw0rd!").confirmPassword("Different1!").build();

        mockMvc.perform(post("/auth/change-password")
                        .with(authenticatedAsPrincipal(User.builder().publicUserId("user-1").build(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authenticationService, never()).changePassword(any(), any(), any(), any());
    }

    @Test
    void verifyEmail_returns200() throws Exception {
        VerifyEmailDto dto = new VerifyEmailDto();
        dto.setEmail("ada@afrochow.com");
        dto.setCode("123456");
        when(authenticationService.verifyEmail("ada@afrochow.com", "123456"))
                .thenReturn("Email verified successfully");

        mockMvc.perform(post("/auth/verify-email")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    void verifyEmail_invalidCodeFormat_returns400() throws Exception {
        VerifyEmailDto dto = new VerifyEmailDto();
        dto.setEmail("ada@afrochow.com");
        dto.setCode("abc");

        mockMvc.perform(post("/auth/verify-email")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(authenticationService, never()).verifyEmail(any(), any());
    }

    @Test
    void resendVerification_returns200() throws Exception {
        ResendVerificationDto dto = new ResendVerificationDto();
        dto.setEmail("ada@afrochow.com");

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent successfully"));

        verify(authenticationService).resendVerificationEmail("ada@afrochow.com");
    }
}
