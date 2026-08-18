package com.afrochow.user.service;

import com.afrochow.auth.service.AuthenticationService;
import com.afrochow.common.enums.Role;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.dto.UserUpdateRequestDto;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OutboxEventService outboxEventService;
    @Mock private AuthenticationService authenticationService;

    @InjectMocks private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(1L).publicUserId("CUS1").email("customer@example.com")
                .role(Role.CUSTOMER).isActive(true).build();
    }

    @Test
    void updateUser_savesAndReturns() {
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.updateUser(user);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void updatePassword_encodesAndSaves() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("encoded-pass");

        userService.updatePassword("customer@example.com", "NewPass1!");

        assertThat(user.getPassword()).isEqualTo("encoded-pass");
        verify(userRepository).save(user);
    }

    @Test
    void updatePassword_userNotFound_throwsRuntimeException() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updatePassword("ghost@example.com", "x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateRole_changesRoleAndSaves() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        userService.updateRole("customer@example.com", Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(user);
    }

    @Test
    void toggleActiveStatus_flipsFlag() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        userService.toggleActiveStatus("customer@example.com");

        assertThat(user.getIsActive()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void toggleActiveStatus_calledTwice_flipsBackToTrue() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        userService.toggleActiveStatus("customer@example.com");
        userService.toggleActiveStatus("customer@example.com");

        assertThat(user.getIsActive()).isTrue();
    }

    @Test
    void findByEmail_found_returnsUser() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        assertThat(userService.findByEmail("customer@example.com")).isEqualTo(user);
    }

    @Test
    void findByEmail_notFound_throwsRuntimeException() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("ghost@example.com"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void findByPublicUserId_found_returnsUser() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(user));

        assertThat(userService.findByPublicUserId("CUS1")).isEqualTo(user);
    }

    @Test
    void findByPublicUserId_notFound_throwsRuntimeException() {
        when(userRepository.findByPublicUserId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByPublicUserId("ghost"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void requireAuthenticatedUser_found_returnsUser() {
        CustomUserDetails principal = principal("CUS1");
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(user));

        assertThat(userService.requireAuthenticatedUser(principal)).isEqualTo(user);
    }

    @Test
    void requireAuthenticatedUser_missingPrincipal_throwsEntityNotFound() {
        assertThatThrownBy(() -> userService.requireAuthenticatedUser(null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateProfile_appliesProvidedFieldsAndNormalizesPhone() {
        CustomUserDetails principal = principal("CUS1");
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserUpdateRequestDto request = UserUpdateRequestDto.builder()
                .firstName("Ayo")
                .lastName("O")
                .phone("(403) 555-1234")
                .email("ayo@example.com")
                .build();

        User result = userService.updateProfile(principal, request);

        assertThat(result).isEqualTo(user);
        assertThat(user.getFirstName()).isEqualTo("Ayo");
        assertThat(user.getLastName()).isEqualTo("O");
        assertThat(user.getPhone()).isEqualTo("4035551234");
        assertThat(user.getEmail()).isEqualTo("ayo@example.com");
        verify(userRepository).save(user);
    }

    @Test
    void deleteAccount_correctPassword_softDeletesLogsOutAndQueuesEmail() throws Exception {
        user.setPassword("hashed-password");
        user.setFirstName("Ade");
        CustomUserDetails principal = principal("CUS1");
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);

        userService.deleteAccount(principal, "correct-password", null, null);

        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getScheduledForDeletionAt()).isNotNull();
        verify(userRepository).save(user);
        verify(authenticationService).logoutAllDevices(null, null);
        verify(outboxEventService).accountDeletionRequested("CUS1", "customer@example.com", "Ade");
    }

    @Test
    void deleteAccount_wrongPassword_throwsAndDoesNotMutate() throws Exception {
        user.setPassword("hashed-password");
        CustomUserDetails principal = principal("CUS1");
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(principal, "wrong-password", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incorrect password");

        assertThat(user.getIsActive()).isTrue();
        verify(userRepository, never()).save(user);
        verify(authenticationService, never()).logoutAllDevices(any(), any());
        verify(outboxEventService, never()).accountDeletionRequested(anyString(), anyString(), anyString());
    }

    @Test
    void softDeleteUser_deactivatesAndSchedulesDeletion() {
        userService.softDeleteUser(user);

        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getScheduledForDeletionAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void queueAccountDeletionEmail_firesOutboxEvent() {
        userService.queueAccountDeletionEmail("CUS1", "customer@example.com", "Ade");

        verify(outboxEventService).accountDeletionRequested("CUS1", "customer@example.com", "Ade");
    }

    @Test
    void reactivateUser_reactivatesAndClearsDeletionSchedule() {
        user.setIsActive(false);
        user.setScheduledForDeletionAt(java.time.LocalDateTime.now());

        userService.reactivateUser(user);

        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getScheduledForDeletionAt()).isNull();
        verify(userRepository).save(user);
    }

    private CustomUserDetails principal(String publicUserId) {
        User principalUser = User.builder().publicUserId(publicUserId).build();
        return new CustomUserDetails(principalUser, List.of());
    }
}
