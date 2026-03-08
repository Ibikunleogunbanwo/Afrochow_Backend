package com.afrochow.security.model;

import com.afrochow.user.model.User;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Custom UserDetails implementation that wraps User entity
 *
 * Provides Spring Security with user authentication and authorization information
 * while exposing additional user-specific fields needed by the application.
 */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    /**
     * -- GETTER --
     *  Get the underlying User entity
     *  Use with caution - prefer using specific getters
     */
    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Get the database ID of the user
     * Useful for service layer operations that need the internal ID
     */
    public Long getUserId() {
        return user.getUserId();
    }

    /**
     * Get the public-facing user ID
     * This is the ID exposed to external systems/APIs
     */
    public String getPublicUserId() {
        return user.getPublicUserId();
    }

    /**
     * Get the username (used for display and identification)
     */
    @Nonnull
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * Get the email address
     */
    public String getEmail() {
        return user.getEmail();
    }

    /**
     * Get the encrypted password
     */
    @Nonnull
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Get the user's granted authorities (roles and permissions)
     */
    @Nonnull
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * Check if the account is enabled
     * User must be both active and email verified
     */
    @Override
    public boolean isEnabled() {
        boolean isActive = user.getIsActive() != null && user.getIsActive();
        boolean emailVerified = user.getEmailVerified() != null && user.getEmailVerified();
        return isActive && emailVerified;
    }

    /**
     * Check if the account is not expired
     */
    @Override
    public boolean isAccountNonExpired() {
        return true; // Implement if you have account expiration logic
    }

    /**
     * Check if the account is not locked
     */
    @Override
    public boolean isAccountNonLocked() {
        return true; // Implement if you have account locking logic
    }

    /**
     * Check if the credentials are not expired
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Implement if you have password expiration logic
    }

}