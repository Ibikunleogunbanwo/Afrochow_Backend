package com.afrochow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.*;

@ToString(exclude = {"password", "confirmPassword"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseRegistrationRequest {

    protected String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    protected String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    protected String password;

    @NotBlank(message = "Confirm password is required")
    protected String confirmPassword;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    protected String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    protected String lastName;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^(\\+?1[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}$",
            message = "Phone number must be a valid Canadian number (e.g. 4161234567 or +1 416-123-4567)"
    )
    protected String phone;

    private String profileImageUrl;

    @AssertTrue(message = "You must accept the terms and conditions")
    protected Boolean acceptTerms;

    public abstract String getUsername();

    @AssertTrue(message = "Passwords do not match")
    @JsonIgnore
    public boolean isPasswordMatching() {
        return getPassword() != null && getPassword().equals(getConfirmPassword());
    }

}

