package com.afrochow.user;

import com.afrochow.auth.service.AuthenticationService;
import com.afrochow.common.enums.Role;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.dto.DeleteAccountRequestDto;
import com.afrochow.user.dto.UserUpdateRequestDto;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for UserController.
 *
 * UserController constructor-injects {@code UserRepository} directly (used
 * in {@code requireAuthenticatedUser} to re-load the user from
 * {@code userDetails.getPublicUserId()}), so — like PromotionControllerTest —
 * this needs a {@code @MockitoBean} for the repository, not just the
 * service. All endpoints take {@code @AuthenticationPrincipal
 * CustomUserDetails}, covered via {@code authenticatedAsPrincipal}.
 */
@ControllerSliceTest(UserController.class)
class UserControllerTest extends AbstractControllerTest {

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private UserService userService;
    @MockitoBean private AuthenticationService authenticationService;
    @MockitoBean private PasswordEncoder passwordEncoder;

    private static final String PUBLIC_USER_ID = "user-1";

    private User principalUser() {
        return User.builder().publicUserId(PUBLIC_USER_ID).build();
    }

    private User storedUser() {
        return User.builder()
                .publicUserId(PUBLIC_USER_ID)
                .email("ade@example.com")
                .firstName("Ade")
                .lastName("O")
                .phone("+14035551234")
                .password("hashed-password")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();
    }

    @Test
    void getProfile_returns200() throws Exception {
        when(userRepository.findByPublicUserId(PUBLIC_USER_ID)).thenReturn(Optional.of(storedUser()));

        mockMvc.perform(get("/user/profile")
                        .with(authenticatedAsPrincipal(principalUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ade@example.com"));
    }

    @Test
    void getProfile_userNotFound_returns404() throws Exception {
        when(userRepository.findByPublicUserId(PUBLIC_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/user/profile")
                        .with(authenticatedAsPrincipal(principalUser(), "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProfile_valid_returns200() throws Exception {
        when(userRepository.findByPublicUserId(PUBLIC_USER_ID)).thenReturn(Optional.of(storedUser()));
        when(userService.updateUser(any(User.class))).thenReturn(storedUser());

        UserUpdateRequestDto request = UserUpdateRequestDto.builder()
                .firstName("Adebayo")
                .build();

        mockMvc.perform(put("/user/profile")
                        .with(authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).updateUser(any(User.class));
    }

    @Test
    void updateProfile_invalidEmail_returns400() throws Exception {
        UserUpdateRequestDto request = UserUpdateRequestDto.builder()
                .email("not-an-email")
                .build();

        mockMvc.perform(put("/user/profile")
                        .with(authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(any());
    }

    @Test
    void deleteAccount_correctPassword_returns200() throws Exception {
        User stored = storedUser();
        when(userRepository.findByPublicUserId(PUBLIC_USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("correct-password", stored.getPassword())).thenReturn(true);
        doNothing().when(userService).softDeleteUser(stored);
        doNothing().when(authenticationService).logoutAllDevices(any(), any());
        doNothing().when(userService).queueAccountDeletionEmail(anyString(), anyString(), anyString());

        DeleteAccountRequestDto request = new DeleteAccountRequestDto();
        request.setPassword("correct-password");

        mockMvc.perform(delete("/user/account")
                        .with(authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).softDeleteUser(stored);
    }

    @Test
    void deleteAccount_wrongPassword_returns400() throws Exception {
        User stored = storedUser();
        when(userRepository.findByPublicUserId(PUBLIC_USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrong-password", stored.getPassword())).thenReturn(false);

        DeleteAccountRequestDto request = new DeleteAccountRequestDto();
        request.setPassword("wrong-password");

        mockMvc.perform(delete("/user/account")
                        .with(authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).softDeleteUser(any());
    }

    @Test
    void deleteAccount_missingPassword_returns400WithValidationErrors() throws Exception {
        DeleteAccountRequestDto request = new DeleteAccountRequestDto();
        request.setPassword("");

        mockMvc.perform(delete("/user/account")
                        .with(authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).softDeleteUser(any());
    }
}
