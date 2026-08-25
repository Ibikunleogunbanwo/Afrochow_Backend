package com.afrochow.auth.service;

import com.afrochow.auth.dto.LoginResponseDto;
import com.afrochow.common.enums.AuthProvider;
import com.afrochow.common.enums.Role;
import com.afrochow.common.exceptions.CustomerWaitlistModeException;
import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.security.JwtTokenProvider;
import com.afrochow.security.service.RefreshTokenService;
import com.afrochow.user.mapper.UserMapper;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private OutboxEventService outboxEventService;
    @Mock private HttpClient httpClient;
    @Mock private GoogleIdTokenVerifier verifier;
    @Mock private GoogleIdToken token;

    private GoogleAuthService service;
    private HttpServletRequest httpRequest;
    private HttpServletResponse httpResponse;

    @BeforeEach
    void setUp() throws Exception {
        service = new GoogleAuthService(
                userRepository, userMapper, jwtTokenProvider, refreshTokenService, outboxEventService,
                "test-client-id", "test-client-secret", "https://afrochow.ca/api/auth/google/callback",
                "", false, httpClient);
        ReflectionTestUtils.setField(service, "verifier", verifier);

        httpRequest = mock(HttpServletRequest.class);
        httpResponse = mock(HttpServletResponse.class);
    }

    private void stubTokenExchange(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    private void stubValidVerification() throws Exception {
        when(verifier.verify(anyString())).thenReturn(token);
    }

    private GoogleIdToken.Payload validPayload() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-123");
        payload.setEmail("customer@example.com");
        payload.set("given_name", "Ade");
        payload.set("family_name", "O");
        payload.set("picture", "https://example.com/pic.jpg");
        return payload;
    }

    private User existingCustomerWithoutGoogleId() {
        return User.builder()
                .userId(1L).publicUserId("CUS123").email("customer@example.com")
                .username("adecustomer").firstName("Ade").lastName("O")
                .role(Role.CUSTOMER).authProvider(AuthProvider.EMAIL)
                .isActive(true).emailVerified(true)
                .build();
    }

    private User newCustomer() {
        return User.builder()
                .publicUserId("CUS123").email("customer@example.com").username("adeo1234")
                .firstName("Ade").lastName("O")
                .role(Role.CUSTOMER).authProvider(AuthProvider.GOOGLE)
                .emailVerified(true).isActive(true).acceptTerms(true)
                .build();
    }

    // ========== happy path ==========

    @Test
    void login_success_linkingGoogleIdToExistingAccount_returnsLoginResponse() throws Exception {
        stubTokenExchange(200, "{\"id_token\":\"id-token-123\"}");
        stubValidVerification();
        when(token.getPayload()).thenReturn(validPayload());
        User existing = existingCustomerWithoutGoogleId();
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(existing));
        when(userMapper.toLoginResponse(existing))
                .thenReturn(LoginResponseDto.builder().publicUserId("CUS123").role("CUSTOMER").build());

        LoginResponseDto response = service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse);

        assertThat(response.getPublicUserId()).isEqualTo("CUS123");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        assertThat(existing.getGoogleId()).isEqualTo("google-sub-123");
        verify(userRepository, times(2)).save(existing);
        verify(userMapper).toLoginResponse(existing);
        verify(outboxEventService, never()).userRegistered(any(), any(), any(), any());
        verify(httpResponse, times(2)).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void login_success_createsNewCustomer_firesWelcomeEmailAndNotification() throws Exception {
        stubTokenExchange(200, "{\"id_token\":\"id-token-123\"}");
        stubValidVerification();
        when(token.getPayload()).thenReturn(validPayload());
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(newCustomer());
        when(userMapper.toLoginResponse(any(User.class)))
                .thenReturn(LoginResponseDto.builder().publicUserId("CUS123").role("CUSTOMER").build());

        LoginResponseDto response = service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse);

        assertThat(response.getPublicUserId()).isEqualTo("CUS123");
        verify(userRepository).saveAndFlush(any(User.class));
        verify(outboxEventService).userRegistered("CUS123", "customer@example.com", "Ade", "CUSTOMER");
        verify(jwtTokenProvider).createToken(any(User.class));
        verify(refreshTokenService).createRefreshTokenForUser(any(User.class), eq(httpRequest));
    }

    // ========== account creation guards ==========

    @Test
    void login_newCustomer_waitlistModeActive_throws() throws Exception {
        service = new GoogleAuthService(
                userRepository, userMapper, jwtTokenProvider, refreshTokenService, outboxEventService,
                "test-client-id", "test-client-secret", "https://afrochow.ca/api/auth/google/callback",
                "", true, httpClient);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        stubTokenExchange(200, "{\"id_token\":\"id-token-123\"}");
        stubValidVerification();
        when(token.getPayload()).thenReturn(validPayload());
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse))
                .isInstanceOf(CustomerWaitlistModeException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void login_vendorContext_unknownEmail_throwsResourceNotFound() throws Exception {
        stubTokenExchange(200, "{\"id_token\":\"id-token-123\"}");
        stubValidVerification();
        when(token.getPayload()).thenReturn(validPayload());
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticateWithGoogle(
                "auth-code", "vendor", httpRequest, httpResponse))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void login_existingVendorByEmail_linkingGoogleId_returnsExistingAccount() throws Exception {
        stubTokenExchange(200, "{\"id_token\":\"id-token-123\"}");
        stubValidVerification();
        when(token.getPayload()).thenReturn(validPayload());
        User vendor = User.builder()
                .userId(2L).publicUserId("VEN456").email("vendor@example.com")
                .role(Role.VENDOR).authProvider(AuthProvider.EMAIL)
                .isActive(true).emailVerified(true)
                .build();
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(vendor));
        when(userMapper.toLoginResponse(vendor))
                .thenReturn(LoginResponseDto.builder().publicUserId("VEN456").role("VENDOR").build());

        LoginResponseDto response = service.authenticateWithGoogle(
                "auth-code", "vendor", httpRequest, httpResponse);

        assertThat(response.getRole()).isEqualTo("VENDOR");
        assertThat(vendor.getGoogleId()).isEqualTo("google-sub-123");
        verify(userRepository, times(2)).save(vendor);
        verify(outboxEventService, never()).userRegistered(any(), any(), any(), any());
    }

    // ========== token exchange failures ==========

    @Test
    void login_tokenExchange_non200_throws() throws Exception {
        stubTokenExchange(500, "error");

        assertThatThrownBy(() -> service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to exchange authorization code");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void login_tokenExchange_missingIdToken_throws() throws Exception {
        stubTokenExchange(200, "{\"access_token\":\"x\"}");

        assertThatThrownBy(() -> service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No id_token");
    }

    @Test
    void login_tokenExchange_ioError_throws() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token exchange failed");
    }

    // ========== token verification failures ==========

    @Test
    void login_invalidGoogleToken_throws() throws Exception {
        stubTokenExchange(200, "{\"id_token\":\"id-token-123\"}");
        when(verifier.verify(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verification failed");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void login_verificationFailure_throws() throws Exception {
        stubTokenExchange(200, "{\"id_token\":\"id-token-123\"}");
        when(verifier.verify(anyString())).thenThrow(new GeneralSecurityException("bad signature"));

        assertThatThrownBy(() -> service.authenticateWithGoogle(
                "auth-code", "customer", httpRequest, httpResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verification failed");
    }
}