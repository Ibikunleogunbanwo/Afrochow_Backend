package com.afrochow.admin.controller;

import com.afrochow.common.enums.Role;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.security.service.LoginAttemptService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for AdminUserManagementController.
 *
 * Talks to {@code UserRepository}/{@code OrderRepository} directly (no
 * service layer). No endpoint takes an authentication parameter — access
 * control is class-level {@code @PreAuthorize("@deptAccess.can('USERS')")}
 * (with role-change/delete overriding to {@code hasRole('SUPERADMIN')}),
 * neither exercised in this slice (see ControllerSliceTest javadoc).
 *
 * Sample users are ADMIN role with no customer/vendor profile attached, so
 * {@code batchCountOrders}/{@code countOrders} short-circuit without calling
 * OrderRepository (its lists come back empty), keeping most tests free of
 * OrderRepository stubbing.
 */
@ControllerSliceTest(AdminUserManagementController.class)
class AdminUserManagementControllerTest extends AbstractControllerTest {

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private LoginAttemptService loginAttemptService;
    @MockitoBean private OrderRepository orderRepository;

    private User sampleUser(String publicUserId, Role role) {
        return User.builder()
                .userId(1L)
                .publicUserId(publicUserId)
                .email("user@afrochow.com")
                .firstName("Ada")
                .lastName("User")
                .role(role)
                .isActive(true)
                .emailVerified(true)
                .build();
    }

    @Test
    void getAllUsers_default_returns200() throws Exception {
        Page<User> page = new PageImpl<>(List.of(sampleUser("user-1", Role.CUSTOMER)), PageRequest.of(0, 25), 1);
        when(userRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].publicUserId").value("user-1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getUserById_found_returns200() throws Exception {
        when(userRepository.findByPublicUserId("user-1")).thenReturn(Optional.of(sampleUser("user-1", Role.CUSTOMER)));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(get("/admin/users/{publicUserId}", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value("user-1"));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        when(userRepository.findByPublicUserId("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/users/{publicUserId}", "ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUsersByRole_returns200() throws Exception {
        when(userRepository.findByRole(Role.VENDOR)).thenReturn(List.of(sampleUser("user-1", Role.VENDOR)));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(get("/admin/users/role/{role}", "VENDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getActiveUsers_returns200() throws Exception {
        when(userRepository.findByIsActive(true)).thenReturn(List.of(sampleUser("user-1", Role.CUSTOMER)));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(get("/admin/users/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getInactiveUsers_returns200() throws Exception {
        when(userRepository.findByIsActive(false)).thenReturn(List.of(sampleUser("user-1", Role.CUSTOMER)));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(get("/admin/users/inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void searchUsers_returns200() throws Exception {
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("ada", "ada"))
                .thenReturn(List.of(sampleUser("user-1", Role.CUSTOMER)));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(get("/admin/users/search").param("query", "ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void activateUser_returns200() throws Exception {
        User user = sampleUser("user-1", Role.CUSTOMER);
        user.setIsActive(false);
        when(userRepository.findByPublicUserId("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(patch("/admin/users/{publicUserId}/activate", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void activateUser_superadmin_returns400() throws Exception {
        when(userRepository.findByPublicUserId("super-1")).thenReturn(Optional.of(sampleUser("super-1", Role.SUPERADMIN)));

        mockMvc.perform(patch("/admin/users/{publicUserId}/activate", "super-1"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_returns200() throws Exception {
        User user = sampleUser("user-1", Role.CUSTOMER);
        when(userRepository.findByPublicUserId("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(patch("/admin/users/{publicUserId}/deactivate", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void deactivateUser_adminAccount_returns400() throws Exception {
        when(userRepository.findByPublicUserId("admin-1")).thenReturn(Optional.of(sampleUser("admin-1", Role.ADMIN)));

        mockMvc.perform(patch("/admin/users/{publicUserId}/deactivate", "admin-1"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void unlockUserAccount_returns200() throws Exception {
        User user = sampleUser("user-1", Role.CUSTOMER);
        when(userRepository.findByPublicUserId("user-1")).thenReturn(Optional.of(user));
        when(loginAttemptService.unlockAccount(anyString())).thenReturn(true);
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(patch("/admin/users/{publicUserId}/unlock", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account unlocked successfully"));
    }

    @Test
    void changeUserRole_returns200() throws Exception {
        User user = sampleUser("user-1", Role.CUSTOMER);
        when(userRepository.findByPublicUserId("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptService.isAccountLocked(anyString())).thenReturn(false);

        mockMvc.perform(patch("/admin/users/{publicUserId}/role", "user-1").param("newRole", "VENDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VENDOR"));
    }

    @Test
    void changeUserRole_assignSuperadmin_returns400() throws Exception {
        when(userRepository.findByPublicUserId("user-1")).thenReturn(Optional.of(sampleUser("user-1", Role.CUSTOMER)));

        mockMvc.perform(patch("/admin/users/{publicUserId}/role", "user-1").param("newRole", "SUPERADMIN"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_returns200() throws Exception {
        User user = sampleUser("user-1", Role.CUSTOMER);
        when(userRepository.findByPublicUserId("user-1")).thenReturn(Optional.of(user));
        // UserRepository extends both CrudRepository (delete(T)) and
        // JpaSpecificationExecutor (delete(DeleteSpecification<T>)) — an
        // unqualified delete(user) call is ambiguous, so we go through the
        // CrudRepository view to pick the entity-delete overload.
        CrudRepository<User, Long> crudUserRepository = userRepository;
        doNothing().when(crudUserRepository).delete(user);

        mockMvc.perform(delete("/admin/users/{publicUserId}", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(crudUserRepository).delete(user);
    }

    @Test
    void deleteUser_superadmin_returns400() throws Exception {
        when(userRepository.findByPublicUserId("super-1")).thenReturn(Optional.of(sampleUser("super-1", Role.SUPERADMIN)));

        mockMvc.perform(delete("/admin/users/{publicUserId}", "super-1"))
                .andExpect(status().isBadRequest());

        CrudRepository<User, Long> crudUserRepository = userRepository;
        verify(crudUserRepository, never()).delete(any());
    }

    @Test
    void getUserStats_returns200() throws Exception {
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByRole(Role.CUSTOMER)).thenReturn(70L);
        when(userRepository.countByRole(Role.VENDOR)).thenReturn(20L);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(9L);
        when(userRepository.countByRole(Role.SUPERADMIN)).thenReturn(1L);
        when(userRepository.countByIsActiveTrue()).thenReturn(95L);

        mockMvc.perform(get("/admin/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(100))
                .andExpect(jsonPath("$.data.inactiveUsers").value(5));
    }
}
