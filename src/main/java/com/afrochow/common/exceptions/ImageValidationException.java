package com.afrochow.common.exceptions;

import java.io.Serial;

/**
 * Exception thrown when image validation fails
 */
public class ImageValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ImageValidationException(String message) {
        super(message);
    }

    public ImageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}