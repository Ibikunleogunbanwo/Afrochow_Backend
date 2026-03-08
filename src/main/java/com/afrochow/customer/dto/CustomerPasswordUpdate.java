package com.afrochow.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPasswordUpdate {
    private String oldPassword;
    private String newPassword;
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
