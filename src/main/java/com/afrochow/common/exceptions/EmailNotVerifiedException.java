package com.afrochow.common.exceptions;

import lombok.Getter;

/**
 * Exception thrown when user tries to login with unverified email
 */
@Getter
public class EmailNotVerifiedException extends RuntimeException {

    private final String email;

    public EmailNotVerifiedException(String message, String email) {
        super(message);
        this.email = email;
    }
}
