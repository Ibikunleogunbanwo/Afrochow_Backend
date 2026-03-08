package com.afrochow.user.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequestDto {

    private String email;           // Optional
    private String password;        // Optional
    private String confirmPassword; // Optional, for password change
    private String firstName;       // Optional
    private String lastName;        // Optional
    private String phone;           // Optional

    /**
     * Optional helper: check if password and confirmPassword match
     */
    public boolean isPasswordMatching() {
        if (password == null && confirmPassword == null) return true; // no password change
        if (password == null || confirmPassword == null) return false;
        return password.equals(confirmPassword);
    }
}
