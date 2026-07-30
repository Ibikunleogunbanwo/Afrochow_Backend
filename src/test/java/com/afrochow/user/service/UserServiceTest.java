package com.afrochow.user.service;

import com.afrochow.common.enums.Role;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OutboxEventService outboxEventService;

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
}
