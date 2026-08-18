package com.afrochow.common;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Helper for responses that need explicit HTTP status control.
 *
 * <p>{@link ApiResponse} defines the JSON body shape. This class wraps that
 * body in a {@link ResponseEntity} when a controller needs to choose a status
 * such as 201 CREATED, 204 NO CONTENT, or 400 BAD REQUEST.
 */
public class ResponseBuilder {

    private ResponseBuilder() {
    }

    // ==================== SUCCESS RESPONSES ====================

    /** 200 OK with the default success message and data. */
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ok(ApiResponse.success(data));
    }

    /** 200 OK with a custom success message and data. */
    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ok(ApiResponse.success(message, data));
    }

    /** 200 OK with a custom success message and no data. */
    public static <T> ResponseEntity<ApiResponse<T>> ok(String message) {
        return ok(ApiResponse.success(message));
    }

    /** 201 CREATED with the default created message and data. */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return withStatus(HttpStatus.CREATED, ApiResponse.success("Resource created successfully", data));
    }

    /** 201 CREATED with a custom success message and data. */
    public static <T> ResponseEntity<ApiResponse<T>> created(String message, T data) {
        return withStatus(HttpStatus.CREATED, ApiResponse.success(message, data));
    }

    /** 204 NO CONTENT with no JSON body. */
    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    // ==================== PAGINATED RESPONSES ====================

    /** 200 OK with paginated data and the default page message. */
    public static <T> ResponseEntity<ApiResponse<ApiResponse.PageResponse<T>>> page(Page<T> page) {
        return ok(ApiResponse.successPage(page));
    }

    /** 200 OK with paginated data and a custom message. */
    public static <T> ResponseEntity<ApiResponse<ApiResponse.PageResponse<T>>> page(String message, Page<T> page) {
        return ok(ApiResponse.successPage(message, page));
    }

    // ==================== ERROR RESPONSES ====================

    /** 400 BAD REQUEST. */
    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return withStatus(HttpStatus.BAD_REQUEST, ApiResponse.badRequest(message));
    }

    /** 400 BAD REQUEST with field-level validation errors. */
    public static <T> ResponseEntity<ApiResponse<T>> validationError(String message, Map<String, String> errors) {
        return withStatus(HttpStatus.BAD_REQUEST, ApiResponse.validationError(message, errors));
    }

    /** 401 UNAUTHORIZED with the default message. */
    public static <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return unauthorized(null);
    }

    /** 401 UNAUTHORIZED with a custom message. */
    public static <T> ResponseEntity<ApiResponse<T>> unauthorized(String message) {
        return withStatus(HttpStatus.UNAUTHORIZED, ApiResponse.unauthorized(message));
    }

    /** 403 FORBIDDEN with the default message. */
    public static <T> ResponseEntity<ApiResponse<T>> forbidden() {
        return forbidden(null);
    }

    /** 403 FORBIDDEN with a custom message. */
    public static <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return withStatus(HttpStatus.FORBIDDEN, ApiResponse.forbidden(message));
    }

    /** 404 NOT FOUND. */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(String resource) {
        return withStatus(HttpStatus.NOT_FOUND, ApiResponse.notFound(resource));
    }

    /** 409 CONFLICT. */
    public static <T> ResponseEntity<ApiResponse<T>> conflict(String message) {
        return withStatus(HttpStatus.CONFLICT, ApiResponse.conflict(message));
    }

    /** 500 INTERNAL SERVER ERROR with the default message. */
    public static <T> ResponseEntity<ApiResponse<T>> internalError() {
        return internalError(null);
    }

    /** 500 INTERNAL SERVER ERROR with a custom message. */
    public static <T> ResponseEntity<ApiResponse<T>> internalError(String message) {
        return withStatus(HttpStatus.INTERNAL_SERVER_ERROR, ApiResponse.internalError(message));
    }

    // ==================== CUSTOM RESPONSES ====================

    /** Custom HTTP status with a success body. */
    public static <T> ResponseEntity<ApiResponse<T>> status(HttpStatus status, String message, T data) {
        return withStatus(status, ApiResponse.success(message, data));
    }

    /** Custom HTTP status with the matching error body when the status is known. */
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

        return withStatus(status, response);
    }


    /** Custom HTTP status with an explicit error code. */
    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, String message, String errorCode) {
        return withStatus(status, ApiResponse.error(message, errorCode));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return withStatus(HttpStatus.OK, body);
    }

    private static <T> ResponseEntity<ApiResponse<T>> withStatus(HttpStatus status, ApiResponse<T> body) {
        return ResponseEntity.status(status).body(body);
    }
}
