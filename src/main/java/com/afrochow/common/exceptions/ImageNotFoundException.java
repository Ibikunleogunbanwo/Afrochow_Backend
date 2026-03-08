
package com.afrochow.common.exceptions;

/**
 * Exception thrown when image validation fails
 */
public class ImageNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageNotFoundException(String message) {
        super(message);
    }

    public ImageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}