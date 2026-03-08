package com.afrochow.common.exceptions;

public class AddressNotPresentException extends RuntimeException {

    public AddressNotPresentException(String message) {
        super(message);
    }

    public AddressNotPresentException(String message, Throwable cause) {
        super(message, cause);
    }
}
