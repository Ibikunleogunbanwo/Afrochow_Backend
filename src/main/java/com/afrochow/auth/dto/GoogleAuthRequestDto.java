package com.afrochow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleAuthRequestDto {

    @NotBlank(message = "Google authorization code is required")
    private String code;

    /**
     * Optional hint for which flow this Google click came from: "customer" (default)
     * or "vendor". Google can only ever authenticate/auto-create CUSTOMER accounts —
     * there's no vendor equivalent since vendor accounts require business details
     * Google can't supply. When context is "vendor" and no existing account matches
     * the Google email, GoogleAuthService rejects instead of silently creating a
     * mismatched CUSTOMER account for someone who came in to register as a vendor.
     */
    private String context;
}
