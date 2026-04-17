package com.afrochow.user;

import com.afrochow.auth.service.AuthenticationService;
import com.afrochow.common.validation.PhoneUtils;
import com.afrochow.common.ApiResponse;
import com.afrochow.email.EmailService;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.dto.DeleteAccountRequestDto;
import com.afrochow.user.dto.UserResponseDto;
import com.afrochow.user.dto.UserUpdateRequestDto;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile management APIs")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /**
     * Load the authenticated user from the principal.
     *
     * <p>Every endpoint here identifies the caller via {@code publicUserId}
     * (the same ID used across /customer/**, /vendor/**, /admin/**). The
     * older {@code findByUsername(authentication.getName())} path was broken
     * for email/Google-registered users whose {@code username} column is null.
     */
    private User requireAuthenticatedUser(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getPublicUserId() == null) {
            throw new jakarta.persistence.EntityNotFoundException("User not found");
        }
        return userRepository.findByPublicUserId(userDetails.getPublicUserId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found"));
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponseDto>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(ApiResponse.success(toDto(user)));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update current user profile", description = "Update firstName, lastName, phone, or email")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserUpdateRequestDto request) {

        User user = requireAuthenticatedUser(userDetails);

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(PhoneUtils.normalize(request.getPhone()));
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail());
        }

        User updated = userService.updateUser(user);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", toDto(updated)));
    }

    @DeleteMapping("/account")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete account", description = "Permanently delete the authenticated user's account after password confirmation")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeleteAccountRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        User user = requireAuthenticatedUser(userDetails);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Incorrect password");
        }

        // Capture details before deletion for the confirmation email
        String email = user.getEmail();
        String firstName = user.getFirstName();

        // ORDER MATTERS: soft-delete FIRST. If this throws (DB constraint,
        // relationship cascade issue, etc.) we don't want to have already
        // logged the user out of every device — that leaves the account
        // active but the user locked out. Only revoke sessions once the
        // account state transition succeeded.
        userService.softDeleteUser(user);

        // Now revoke all sessions and clear cookies
        try {
            authenticationService.logoutAllDevices(httpRequest, httpResponse);
        } catch (Exception e) {
            // Soft-delete already committed — don't fail the request just
            // because session cleanup had a hiccup. Log for investigation.
            log.error("Session revocation failed after soft-delete for user {}: {}",
                    user.getPublicUserId(), e.getMessage(), e);
        }

        // Send confirmation email (non-blocking — failure is logged, not thrown)
        emailService.sendNotificationEmail(
                email,
                firstName,
                "Account Deletion Requested",
                "Your account has been deactivated. You have 30 days to reactivate it by signing back in. " +
                "After that, your profile, addresses, order history and reviews are permanently removed. " +
                "If you did not request this, please contact our support team immediately."
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Account scheduled for deletion. Sign back in within 30 days to reactivate."));
    }

    private UserResponseDto toDto(User user) {
        boolean profileComplete = user.getPhone() != null && !user.getPhone().isBlank();
        String authProvider = user.getAuthProvider() != null ? user.getAuthProvider().name() : "EMAIL";
        return UserResponseDto.builder()
                .publicUserId(user.getPublicUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .isProfileComplete(profileComplete)
                .authProvider(authProvider)
                .build();
    }
}
