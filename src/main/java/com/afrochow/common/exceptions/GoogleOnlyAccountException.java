package com.afrochow.common.exceptions;

public class GoogleOnlyAccountException extends RuntimeException {
    public GoogleOnlyAccountException(String message) {
        super(message);
    }
}
