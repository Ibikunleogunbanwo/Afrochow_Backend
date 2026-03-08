package com.afrochow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.*;

@ToString(exclude = {"password", "confirmPassword"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseRegistrationRequest {
    protected String username;
    protected String email;
    protected String password;
    protected String confirmPassword;
    protected String firstName;
    protected String lastName;
    protected String phone;
    private String profileImageUrl;
    protected Boolean acceptTerms;


    public abstract String getUsername();

    @AssertTrue(message = "Passwords do not match")
    @JsonIgnore
    public boolean isPasswordMatching() {
        return getPassword() != null && getPassword().equals(getConfirmPassword());
    }

}

