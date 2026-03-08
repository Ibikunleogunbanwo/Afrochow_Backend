package com.afrochow.common.exceptions;

import lombok.Getter;

import java.util.List;

/**
 * Exception thrown when password does not meet policy requirements
 */
@Getter
public class PasswordPolicyViolationException extends RuntimeException {

    private final List<String> errors;

    public PasswordPolicyViolationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public PasswordPolicyViolationException(String message) {
        super(message);
        this.errors = List.of(message);
    }
}
