package com.afrochow.user.mapper;

import com.afrochow.auth.dto.LoginResponseDto;
import com.afrochow.common.enums.AuthProvider;
import com.afrochow.common.enums.Role;
import com.afrochow.user.dto.UserResponseDto;
import com.afrochow.user.model.User;
import com.afrochow.vendor.model.VendorProfile;
import org.springframework.stereotype.Component;

/**
 * Centralizes user DTO mapping so controllers stay focused on HTTP concerns.
 */
@Component
public class UserMapper {

    private static final String DEFAULT_AUTH_PROVIDER = AuthProvider.EMAIL.name();

    public UserResponseDto toResponseDto(User user) {
        if (user == null) {
            return null;
        }

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
                .isProfileComplete(hasText(user.getPhone()))
                .authProvider(resolveAuthProvider(user))
                .build();
    }

    /**
     * Shared login/auth response mapping (used by both email and Google auth flows).
     */
    public LoginResponseDto toLoginResponse(User user) {
        LoginResponseDto.LoginResponseDtoBuilder builder = LoginResponseDto.builder()
                .publicUserId(user.getPublicUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isProfileComplete(hasText(user.getPhone()))
                .authProvider(resolveAuthProvider(user));

        // Attach vendor-specific status so the frontend can show appropriate
        // banners for pending-approval or deactivated vendor accounts.
        if (user.getRole() == Role.VENDOR && user.getVendorProfile() != null) {
            VendorProfile vendorProfile = user.getVendorProfile();
            builder.vendorIsActive(vendorProfile.getIsActive())
                   .vendorIsVerified(vendorProfile.getIsVerified());
        }

        return builder.build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveAuthProvider(User user) {
        return user.getAuthProvider() != null
                ? user.getAuthProvider().name()
                : DEFAULT_AUTH_PROVIDER;
    }
}
