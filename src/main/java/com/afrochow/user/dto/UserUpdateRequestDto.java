package com.afrochow.user.dto;
import com.afrochow.common.validation.CanadianPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequestDto {

    @Email(message = "Invalid email format")
    private String email;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String confirmPassword;

    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    private String firstName;

    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    private String lastName;

    @CanadianPhone
    private String phone;

    /**
     * Optional helper: check if password and confirmPassword match
     */
    public boolean isPasswordMatching() {
        if (password == null && confirmPassword == null) return true; // no password change
        if (password == null || confirmPassword == null) return false;
        return password.equals(confirmPassword);
    }
}
