package com.afrochow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for POST /auth/logout-all.
 *
 * <p>Revoking every active refresh token is effectively a self-inflicted denial of
 * service, so we require the caller to re-prove knowledge of the account password
 * before executing it. That way a leaked access token alone cannot be used to
 * lock the legitimate user out of all of their devices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutAllRequestDto {

    @NotBlank(message = "Password is required to sign out of all devices")
    private String password;
}
