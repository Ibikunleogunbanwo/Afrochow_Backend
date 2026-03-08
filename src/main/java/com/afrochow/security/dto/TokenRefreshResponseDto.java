package com.afrochow.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token refresh response DTO for cookie-based auth.
 * Tokens are sent via HTTP-only cookies, not returned in JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRefreshResponseDto {

    private String publicUserId;
    private String username;
    private String email;
    private String role;

}
