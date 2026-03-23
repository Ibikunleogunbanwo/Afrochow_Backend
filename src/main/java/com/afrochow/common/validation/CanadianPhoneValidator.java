package com.afrochow.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CanadianPhoneValidator implements ConstraintValidator<CanadianPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null/blank is allowed — use @NotBlank separately if the field is required
        if (value == null || value.isBlank()) return true;

        String normalized = PhoneUtils.normalize(value);
        return PhoneUtils.isValid(normalized);
    }
}
