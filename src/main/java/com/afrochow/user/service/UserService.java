package com.afrochow.user.service;
import com.afrochow.auth.service.AuthenticationService;
import com.afrochow.common.enums.Role;
import com.afrochow.common.validation.PhoneUtils;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.dto.UserUpdateRequestDto;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventService outboxEventService;
    private final AuthenticationService authenticationService;

    // Profile and account use cases

    /**
     * Updates the currently signed-in user's basic profile fields.
     *
     * <p>Only non-blank values from the request are applied, so callers can
     * send partial updates without accidentally clearing existing data.
     */
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public User updateProfile(CustomUserDetails userDetails, UserUpdateRequestDto request) {
        User user = requireAuthenticatedUser(userDetails);

        applyText(request.getFirstName(), user::setFirstName);
        applyText(request.getLastName(), user::setLastName);
        applyText(request.getPhone(), phone -> user.setPhone(PhoneUtils.normalize(phone)));
        applyText(request.getEmail(), user::setEmail);

        return userRepository.save(user);
    }

    /**
     * Soft-deletes the signed-in user's account after confirming their password.
     *
     * <p>The account is deactivated and scheduled for deletion, active sessions
     * are revoked, and an account-deletion email event is queued.
     */
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void deleteAccount(CustomUserDetails userDetails,
                              String password,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        User user = requireAuthenticatedUser(userDetails);
        verifyPassword(password, user);

        AccountDeletionNotice notice = AccountDeletionNotice.from(user);
        scheduleSoftDeletion(user);
        revokeSessions(user, httpRequest, httpResponse);
        queueAccountDeletionEmail(notice.publicUserId(), notice.email(), notice.firstName());
    }

    // User lookup

    /**
     * Finds a user by email or throws when no matching account exists.
     */
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
    }

    /**
     * Finds a user by their public ID, such as {@code CUS-...} or {@code VEN-...}.
     */
    @Transactional(readOnly = true)
    public User findByPublicUserId(String publicUserId) {
        return userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new RuntimeException("User not found: " + publicUserId));
    }

    /**
     * Load the authenticated user from the principal.
     *
     * <p>Every endpoint here identifies the caller via {@code publicUserId}
     * (the same ID used across /customer/**, /vendor/**, /admin/**). The
     * older {@code findByUsername(authentication.getName())} path was broken
     * for email/Google-registered users whose {@code username} column is null.
     */
    @Transactional(readOnly = true)
    public User requireAuthenticatedUser(CustomUserDetails userDetails) {
        return userRepository.findByPublicUserId(requirePublicUserId(userDetails))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    /**
     * Checks whether the given user has the SUPERADMIN role.
     *
     * <p>This keeps SUPERADMIN checks in the service layer instead of adding
     * another role-specific helper to the entity.
     */
    @Transactional(readOnly = true)
    public boolean isSuperAdmin(User user) {
        return user != null && user.getRole() == Role.SUPERADMIN;
    }

    /**
     * Returns the profile object that matches the user's role.
     *
     * <p>Customers return their customer profile, vendors return their vendor
     * profile, and admins/superadmins return their admin profile.
     */
    @Transactional(readOnly = true)
    public Object getActiveProfile(User user) {
        Objects.requireNonNull(user, "User is required");

        return switch (user.getRole()) {
            case CUSTOMER -> user.getCustomerProfile();
            case VENDOR -> user.getVendorProfile();
            case ADMIN, SUPERADMIN -> user.getAdminProfile();
        };
    }


    // General user administration

    /**
     * Saves a full user entity that has already been modified by the caller.
     */
    @Transactional
    @CacheEvict(value = "users", key = "#user.email")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Changes a user's password after encoding the raw password value.
     */
    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public void updatePassword(String email, String newPassword) {
        User user = findByEmail(email);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Changes a user's role, for example from CUSTOMER to ADMIN.
     */
    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public void updateRole(String email, Role newRole) {
        User user = findByEmail(email);
        user.setRole(newRole);
        userRepository.save(user);
    }

    /**
     * Flips a user's active flag: active users become inactive, inactive users become active.
     */
    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public void toggleActiveStatus(String email) {
        User user = findByEmail(email);
        user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
        userRepository.save(user);
    }

    /**
     * Clears every cached user entry.
     *
     * <p>The empty method body is intentional: the annotation performs the work.
     */
    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsersCache() {
    }

    /**
     * Deactivates a user and marks when deletion was requested.
     */
    @Transactional
    @CacheEvict(value = "users", key = "#user.email")
    public void softDeleteUser(User user) {
        scheduleSoftDeletion(user);
    }

    /**
     * Queues an async email notification for an account deletion request.
     */
    @Transactional
    public void queueAccountDeletionEmail(String publicUserId, String email, String firstName) {
        outboxEventService.accountDeletionRequested(publicUserId, email, firstName);
    }

    /**
     * Restores a previously soft-deleted user account.
     */
    @Transactional
    @CacheEvict(value = "users", key = "#user.email")
    public void reactivateUser(User user) {
        user.reactivate();
        userRepository.save(user);
    }

    /**
     * Applies a setter only when the incoming text has real content.
     */
    private void applyText(String value, Consumer<String> setter) {
        Optional.ofNullable(value)
                .filter(text -> !text.isBlank())
                .ifPresent(setter);
    }

    /**
     * Extracts the public user ID from the authenticated principal.
     */
    private String requirePublicUserId(CustomUserDetails userDetails) {
        return Optional.ofNullable(userDetails)
                .map(CustomUserDetails::getPublicUserId)
                .filter(publicUserId -> !publicUserId.isBlank())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    /**
     * Confirms that the submitted password matches the stored encoded password.
     */
    private void verifyPassword(String rawPassword, User user) {
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect password");
        }
    }

    /**
     * Performs the shared soft-delete state change and persists it.
     */
    private void scheduleSoftDeletion(User user) {
        user.scheduleForDeletion();
        userRepository.save(user);
    }

    /**
     * Logs the user out everywhere after account deletion.
     *
     * <p>Failure here is logged but not rethrown because the account state
     * has already been changed successfully.
     */
    private void revokeSessions(User user, HttpServletRequest request, HttpServletResponse response) {
        try {
            authenticationService.logoutAllDevices(request, response);
        } catch (Exception e) {
            log.error("Session revocation failed after soft-delete for user {}: {}",
                    user.getPublicUserId(), e.getMessage(), e);
        }
    }

    /**
     * Small snapshot of user data needed after the user is soft-deleted.
     */
    private record AccountDeletionNotice(String publicUserId, String email, String firstName) {
        /**
         * Captures email fields before later account/session cleanup work runs.
         */
        private static AccountDeletionNotice from(User user) {
            return new AccountDeletionNotice(
                    user.getPublicUserId(),
                    user.getEmail(),
                    user.getFirstName());
        }
    }
}
