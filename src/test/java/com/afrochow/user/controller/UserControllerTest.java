package com.afrochow.user.controller;

import com.afrochow.common.enums.Role;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.dto.DeleteAccountRequestDto;
import com.afrochow.user.dto.UserUpdateRequestDto;
import com.afrochow.user.mapper.UserMapper;
import com.afrochow.user.model.User;
import com.afrochow.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Controller-layer test for UserController.
 *
 * All endpoints take {@code @AuthenticationPrincipal
 * CustomUserDetails}, covered via {@code authenticatedAsPrincipal}.
 */
@ControllerSliceTest(UserController.class)
@Import(UserMapper.class)
class UserControllerTest extends AbstractControllerTest {

    @MockitoBean private UserService userService;

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
        Mockito.when(userService.requireAuthenticatedUser(ArgumentMatchers.any())).thenReturn(storedUser());

        mockMvc.perform(MockMvcRequestBuilders.get("/user/profile")
                        .with(AbstractControllerTest.authenticatedAsPrincipal(principalUser(), "CUSTOMER")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.email").value("ade@example.com"));
    }

    @Test
    void getProfile_userNotFound_returns404() throws Exception {
        Mockito.when(userService.requireAuthenticatedUser(ArgumentMatchers.any()))
                .thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(MockMvcRequestBuilders.get("/user/profile")
                        .with(AbstractControllerTest.authenticatedAsPrincipal(principalUser(), "CUSTOMER")))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void updateProfile_valid_returns200() throws Exception {
        Mockito.when(userService.updateProfile(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(UserUpdateRequestDto.class)))
                .thenReturn(storedUser());

        UserUpdateRequestDto request = UserUpdateRequestDto.builder()
                .firstName("Adebayo")
                .build();

        mockMvc.perform(MockMvcRequestBuilders.put("/user/profile")
                        .with(AbstractControllerTest.authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true));

        Mockito.verify(userService).updateProfile(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(UserUpdateRequestDto.class));
    }

    @Test
    void updateProfile_invalidEmail_returns400() throws Exception {
        UserUpdateRequestDto request = UserUpdateRequestDto.builder()
                .email("not-an-email")
                .build();

        mockMvc.perform(MockMvcRequestBuilders.put("/user/profile")
                        .with(AbstractControllerTest.authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        Mockito.verify(userService, Mockito.never()).updateProfile(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(UserUpdateRequestDto.class));
    }

    @Test
    void deleteAccount_correctPassword_returns200() throws Exception {
        DeleteAccountRequestDto request = new DeleteAccountRequestDto();
        request.setPassword("correct-password");

        mockMvc.perform(MockMvcRequestBuilders.delete("/user/account")
                        .with(AbstractControllerTest.authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true));

        Mockito.verify(userService).deleteAccount(
                ArgumentMatchers.any(),
                ArgumentMatchers.eq("correct-password"),
                ArgumentMatchers.any(),
                ArgumentMatchers.any());
    }

    @Test
    void deleteAccount_wrongPassword_returns400() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("Incorrect password"))
                .when(userService)
                .deleteAccount(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq("wrong-password"),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());

        DeleteAccountRequestDto request = new DeleteAccountRequestDto();
        request.setPassword("wrong-password");

        mockMvc.perform(MockMvcRequestBuilders.delete("/user/account")
                        .with(AbstractControllerTest.authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        Mockito.verify(userService).deleteAccount(
                ArgumentMatchers.any(),
                ArgumentMatchers.eq("wrong-password"),
                ArgumentMatchers.any(),
                ArgumentMatchers.any());
    }

    @Test
    void deleteAccount_missingPassword_returns400WithValidationErrors() throws Exception {
        DeleteAccountRequestDto request = new DeleteAccountRequestDto();
        request.setPassword("");

        mockMvc.perform(MockMvcRequestBuilders.delete("/user/account")
                        .with(AbstractControllerTest.authenticatedAsPrincipal(principalUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        Mockito.verify(userService, Mockito.never()).deleteAccount(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any());
    }
}
