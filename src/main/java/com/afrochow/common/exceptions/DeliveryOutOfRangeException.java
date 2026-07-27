package com.afrochow.common.exceptions;

/**
 * Thrown when a customer's delivery address is farther from the vendor than
 * the vendor's own configured maxDeliveryDistanceKm.
 */
public class DeliveryOutOfRangeException extends RuntimeException {
    public DeliveryOutOfRangeException(String message) {
        super(message);
    }
}
