package com.afrochow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyEmailDto {
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$")
    private String code;

}