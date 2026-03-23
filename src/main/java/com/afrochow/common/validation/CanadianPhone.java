package com.afrochow.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CanadianPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CanadianPhone {

    String message() default "Please enter a valid 10-digit Canadian phone number (e.g. 403-123-4567)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
