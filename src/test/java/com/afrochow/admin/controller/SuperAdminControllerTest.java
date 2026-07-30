package com.afrochow.admin.controller;

import com.afrochow.common.enums.Role;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for SuperAdminController.
 *
 * Talks to UserRepository directly, no auth param on either endpoint —
 * access control is class-level {@code @PreAuthorize("hasRole('SUPERADMIN')")},
 * not exercised in this slice (see ControllerSliceTest javadoc).
 */
@ControllerSliceTest(SuperAdminController.class)
class SuperAdminControllerTest extends AbstractControllerTest {

    @MockitoBean private UserRepository userRepository;

    private User sampleUser(String publicUserId, Role role) {
        return User.builder().userId(1L).publicUserId(publicUserId).role(role).build();
    }

    @Test
    void promoteToSuperAdmin_returns200() throws Exception {
        User user = sampleUser("admin-1", Role.ADMIN);
        when(userRepository.findByPublicUserId("admin-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/superadmin/users/{publicUserId}/promote", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User promoted to SUPERADMIN successfully"));

        verify(userRepository).save(argThat(u -> u.getRole() == Role.SUPERADMIN));
    }

    @Test
    void promoteToSuperAdmin_notAdmin_returns400() throws Exception {
        when(userRepository.findByPublicUserId("cust-1")).thenReturn(Optional.of(sampleUser("cust-1", Role.CUSTOMER)));

        mockMvc.perform(patch("/superadmin/users/{publicUserId}/promote", "cust-1"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void promoteToSuperAdmin_notFound_returns404() throws Exception {
        when(userRepository.findByPublicUserId("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(patch("/superadmin/users/{publicUserId}/promote", "ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void demoteToAdmin_returns200() throws Exception {
        User user = sampleUser("super-1", Role.SUPERADMIN);
        when(userRepository.findByPublicUserId("super-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/superadmin/users/{publicUserId}/demote", "super-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User demoted to ADMIN successfully"));
    }

    @Test
    void demoteToAdmin_notSuperAdmin_returns400() throws Exception {
        when(userRepository.findByPublicUserId("admin-1")).thenReturn(Optional.of(sampleUser("admin-1", Role.ADMIN)));

        mockMvc.perform(patch("/superadmin/users/{publicUserId}/demote", "admin-1"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }
}
