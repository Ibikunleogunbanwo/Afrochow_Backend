package com.afrochow.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String publicUserId;
    private String username;
    private String email;
    private String role;

    // Vendor-only status fields (null for non-vendor users)
    private Boolean vendorIsActive;
    private Boolean vendorIsVerified;

    // Profile completeness — false for new Google sign-in users missing phone/address
    private Boolean isProfileComplete;

    // Registration method — EMAIL or GOOGLE
    private String authProvider;
}

