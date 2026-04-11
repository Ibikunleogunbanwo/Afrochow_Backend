package com.afrochow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleAuthRequest {

    @NotBlank(message = "Google authorization code is required")
    private String code;
}
