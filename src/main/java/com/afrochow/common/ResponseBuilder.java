package com.afrochow.common;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Response Builder Utility
 * Provides convenient methods to create ResponseEntity wrapped ApiResponse objects
 * Reduces boilerplate code in controllers
 */
public class ResponseBuilder {

    private ResponseBuilder() {
    }

    // ==================== SUCCESS RESPONSES ====================

    /**
     * 200 OK - Success with data
     */
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 200 OK - Success with a custom message and data
     */
    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    /**
     * 200 OK - Success with a message only
     */
    public static <T> ResponseEntity<ApiResponse<T>> ok(String message) {
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    /**
     * 201 CREATED - Resource created successfully
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resource created successfully", data));
    }

    /**
     * 201 CREATED - Resource created with a custom message
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message, data));
    }

    /**
     * 204 NO CONTENT - Success with no response body
     */
    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    // ==================== PAGINATED RESPONSES ====================

    /**
     * 200 OK - Paginated data with a default message
     */
    public static <T> ResponseEntity<ApiResponse<ApiResponse.PageResponse<T>>> page(Page<T> page) {
        return ResponseEntity.ok(ApiResponse.successPage(page));
    }

    /**
     * 200 OK - Paginated data with a custom message
     */
    public static <T> ResponseEntity<ApiResponse<ApiResponse.PageResponse<T>>> page(String message, Page<T> page) {
        return ResponseEntity.ok(ApiResponse.successPage(message, page));
    }

    // ==================== ERROR RESPONSES ====================

    /**
     * 400 BAD REQUEST
     */
    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.badRequest(message));
    }

    /**
     * 400 BAD REQUEST - Validation errors
     */
    public static <T> ResponseEntity<ApiResponse<T>> validationError(String message, Map<String, String> errors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(message, errors));
    }

    /**
     * 401 UNAUTHORIZED
     */
    public static <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.unauthorized(null));
    }

    /**
     * 401 UNAUTHORIZED - With a custom message
     */
    public static <T> ResponseEntity<ApiResponse<T>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.unauthorized(message));
    }

    /**
     * 403 FORBIDDEN
     */
    public static <T> ResponseEntity<ApiResponse<T>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.forbidden(null));
    }

    /**
     * 403 FORBIDDEN - With a custom message
     */
    public static <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.forbidden(message));
    }

    /**
     * 404 NOT FOUND
     */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(String resource) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.notFound(resource));
    }

    /**
     * 409 CONFLICT
     */
    public static <T> ResponseEntity<ApiResponse<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.conflict(message));
    }

    /**
     * 500 INTERNAL SERVER ERROR
     */
    public static <T> ResponseEntity<ApiResponse<T>> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.internalError(null));
    }

    /**
     * 500 INTERNAL SERVER ERROR - With a custom message
     */
    public static <T> ResponseEntity<ApiResponse<T>> internalError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.internalError(message));
    }

    // ==================== CUSTOM RESPONSES ====================

    /**
     * Custom HTTP status with success response
     */
    public static <T> ResponseEntity<ApiResponse<T>> status(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status)
                .body(ApiResponse.success(message, data));
    }

    /**
     * Custom HTTP status with error response
     */
    public static ResponseEntity<ApiResponse<Object>> error(
            HttpStatus status,
            String message
    ) {
        ApiResponse<Object> response;

        switch (status) {
            case BAD_REQUEST ->
                    response = ApiResponse.badRequest(message);
            case UNAUTHORIZED ->
                    response = ApiResponse.unauthorized(message);
            case FORBIDDEN ->
                    response = ApiResponse.forbidden(message);
            case NOT_FOUND ->
                    response = ApiResponse.notFound(message);
            case CONFLICT ->
                    response = ApiResponse.conflict(message);
            case INTERNAL_SERVER_ERROR ->
                    response = ApiResponse.internalError(message);
            default ->
                    response = ApiResponse.error(message, status.name());
        }

        return ResponseEntity.status(status).body(response);
    }


    /**
     * Custom HTTP status with error response and error code
     */
    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, String message, String errorCode) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(message, errorCode));
    }
}