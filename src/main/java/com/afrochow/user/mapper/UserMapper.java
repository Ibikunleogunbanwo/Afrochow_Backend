package com.afrochow.user.mapper;

import com.afrochow.common.enums.AuthProvider;
import com.afrochow.user.dto.UserResponseDto;
import com.afrochow.user.model.User;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveAuthProvider(User user) {
        return user.getAuthProvider() != null
                ? user.getAuthProvider().name()
                : DEFAULT_AUTH_PROVIDER;
    }
}
