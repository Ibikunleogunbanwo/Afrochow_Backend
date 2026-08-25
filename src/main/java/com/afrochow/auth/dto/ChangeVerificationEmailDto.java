package com.afrochow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeVerificationEmailDto {
    @NotBlank(message = "Current email is required")
    @Email(message = "Invalid current email format")
    private String currentEmail;

    @NotBlank(message = "New email is required")
    @Email(message = "Invalid new email format")
    private String newEmail;
}
