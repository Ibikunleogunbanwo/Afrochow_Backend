package com.afrochow.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPasswordUpdate {

    @NotBlank(message = "Current password is required")
    private String oldPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    // Mirror PasswordPolicyService + ChangePasswordRequestDto + frontend Zod schema
    // (4 character classes). Drift here causes confusing UX: the DTO regex passes
    // but PasswordPolicyService.validatePassword throws inside the service layer.
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String newPassword;

    @NotBlank(message = "Confirm new password is required")
    private String confirmNewPassword;


    /**
     * Optional helper: check if password and confirmPassword match
     */
    public boolean isPasswordMatching() {
        if (newPassword == null && confirmNewPassword == null) return true; // nothing to update
        if (newPassword == null || confirmNewPassword == null) return false;
        return newPassword.equals(confirmNewPassword);
    }
}
