package com.afrochow.common.exceptions;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(String productName) {
        super("Product is currently unavailable: " + productName);
    }
}