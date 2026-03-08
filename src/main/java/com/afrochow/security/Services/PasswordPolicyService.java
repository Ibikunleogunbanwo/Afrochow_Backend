package com.afrochow.security.Services;

import com.afrochow.common.exceptions.PasswordPolicyViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for validating password policies
 *
 * PASSWORD POLICY REQUIREMENTS:
 * - Minimum 8 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one digit
 * - At least one special character (@$!%*?&)
 */
@Slf4j
@Service
public class PasswordPolicyService {

    // Password policy configuration
    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[@$!%*?&]");

    /**
     * Validate password against all policy requirements
     *
     * @param password The password to validate
     * @throws PasswordPolicyViolationException if password does not meet requirements
     */
    public void validatePassword(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            throw new PasswordPolicyViolationException(List.of("Password cannot be null or empty"));
        }

        // Check minimum length
        if (password.length() < MIN_LENGTH) {
            errors.add(String.format("Password must be at least %d characters long", MIN_LENGTH));
        }

        // Check for an uppercase letter
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter");
        }

        // Check for a lowercase letter
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter");
        }

        // Check for digit
        if (!DIGIT_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one digit");
        }

        // Check for special character
        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one special character (@$!%*?&)");
        }

        // Throw exception if there are any errors
        if (!errors.isEmpty()) {
            log.warn("Password policy validation failed: {} violations", errors.size());
            throw new PasswordPolicyViolationException(errors);
        }

        log.debug("Password policy validation successful");
    }

    /**
     * Check if the password meets all requirements without throwing exception
     *
     * @param password The password to check
     * @return true if password meets all requirements, false otherwise
     */
    public boolean isPasswordValid(String password) {
        try {
            validatePassword(password);
            return true;
        } catch (PasswordPolicyViolationException e) {
            return false;
        }
    }

    /**
     * Get password policy requirements as a list of strings
     *
     * @return List of password policy requirements
     */
    public List<String> getPasswordRequirements() {
        return List.of(
                String.format("Minimum %d characters", MIN_LENGTH),
                "At least one uppercase letter",
                "At least one lowercase letter",
                "At least one digit",
                "At least one special character (@$!%*?&)"
        );
    }
}
