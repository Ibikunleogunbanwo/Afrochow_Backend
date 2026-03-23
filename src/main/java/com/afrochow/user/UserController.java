package com.afrochow.user;

import com.afrochow.auth.service.AuthenticationService;
import com.afrochow.common.validation.PhoneUtils;
import com.afrochow.common.ApiResponse;
import com.afrochow.user.dto.UserResponseDto;
import com.afrochow.user.dto.UserUpdateRequestDto;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile management APIs")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthenticationService authenticationService;

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponseDto>> getProfile(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(toDto(user)));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update current user profile", description = "Update firstName, lastName, phone, or email")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UserUpdateRequestDto request) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found"));

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
    @Operation(summary = "Delete account", description = "Deactivate the authenticated user's account and revoke all sessions")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found"));

        user.setIsActive(false);
        userRepository.save(user);

        authenticationService.logoutAllDevices(httpRequest, httpResponse);

        return ResponseEntity.ok(ApiResponse.success("Account deactivated successfully"));
    }

    private UserResponseDto toDto(User user) {
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
                .build();
    }
}
