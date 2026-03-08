package com.afrochow.common.exceptions;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public EmailAlreadyExistsException(String email, String message) {
        super(String.format("Email '%s' %s", email, message));
    }
}
