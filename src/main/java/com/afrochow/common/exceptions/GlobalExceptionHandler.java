package com.afrochow.common.exceptions;

import com.afrochow.common.ApiResponse;
import com.afrochow.vendor.dto.ValidationErrorDto;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler for Afrochow Application
 *
 * Handles all exceptions thrown across the application and converts them
 * into consistent ApiResponse objects. Organized by exception category.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_AUTH_ERROR = "Authentication failed. Please check your credentials.";

    // ═════════════════════════════════════════════════════════════
    //  JWT & TOKEN AUTHENTICATION EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    /**
     * Handle custom JWT Authentication Exception
     */
    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleJwtAuthenticationException(
            JwtAuthenticationException ex, WebRequest request) {

        logger.warn("JWT Authentication failed: {} (Error Code: {})",
                ex.getMessage(), ex.getErrorCode());

        // Return generic message to prevent information leakage
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_ERROR, request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidTokenException(
            JwtExpiredTokenException ex, WebRequest request) {

        logger.warn("Invalid password reset token: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                "Your Token has expired. Please request another token.", request);
    }


    /**
     * Handle JWT Expired Token Exception (custom)
     */
    @ExceptionHandler(JwtExpiredTokenException.class)
    public ResponseEntity<ApiResponse<Object>> handleJwtExpiredTokenException(
            JwtExpiredTokenException ex, WebRequest request) {

        logger.warn("JWT Token expired: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                "Your session has expired. Please login again.", request);
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleImageNotFoundException(ImageNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Image not found"));
    }



    /**
     * Handle io.jsonwebtoken.ExpiredJwtException (from JJWT library)
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponse<Object>> handleExpiredJwtException(
            ExpiredJwtException ex, WebRequest request) {

        logger.warn("JWT Token expired: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                "Your session has expired. Please login again.", request);
    }

    /**
     * Handle malformed JWT tokens
     */
    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<ApiResponse<Object>> handleMalformedJwtException(
            MalformedJwtException ex, WebRequest request) {

        logger.warn("Malformed JWT token: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_ERROR, request);
    }

    /**
     * Handle unsupported JWT tokens
     */
    @ExceptionHandler(UnsupportedJwtException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedJwtException(
            UnsupportedJwtException ex, WebRequest request) {

        logger.warn("Unsupported JWT token: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_ERROR, request);
    }

    /**
     * Handle JWT signature validation failures
     */
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ApiResponse<Object>> handleSignatureException(
            SignatureException ex, WebRequest request) {

        logger.warn("JWT signature validation failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_ERROR, request);
    }

    /**
     * Handle user not found (prevent username enumeration)
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUsernameNotFoundException(
            UsernameNotFoundException ex, WebRequest request) {

        logger.warn("User not found: {}", ex.getMessage());
        // Return generic message to prevent username enumeration
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_ERROR, request);
    }

    // ═════════════════════════════════════════════════════════════
    //  SPRING SECURITY AUTHENTICATION EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {
        logger.warn("Bad credentials: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Object>> handleDisabledException(
            DisabledException ex, WebRequest request) {
        logger.warn("Account disabled: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                "Your account has been disabled. Please contact support.", request);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleLockedException(
            LockedException ex, WebRequest request) {
        logger.warn("Account locked: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                "Your account has been locked. Please contact support.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_ERROR, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        logger.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN,
                "You don't have permission to access this resource", request);
    }

    // ═════════════════════════════════════════════════════════════
    //  CUSTOM AUTHENTICATION & AUTHORIZATION EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorized(
            UnauthorizedException ex, WebRequest request) {
        logger.warn("Unauthorized access: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(DisabledAccountException.class)
    public ResponseEntity<ApiResponse<Object>> handleDisabledAccount(
            DisabledAccountException ex, WebRequest request) {
        logger.warn("Account disabled: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbidden(
            ForbiddenException ex, WebRequest request) {
        logger.warn("Forbidden access: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest request) {
        logger.warn("Invalid credentials: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientPermission(
            InsufficientPermissionException ex, WebRequest request) {
        logger.warn("Insufficient permission: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailNotVerified(
            EmailNotVerifiedException ex, WebRequest request) {
        logger.warn("Email not verified for: {}", ex.getEmail());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // ═════════════════════════════════════════════════════════════
    //  RATE LIMITING & SECURITY EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimitExceeded(
            RateLimitExceededException ex, WebRequest request) {
        logger.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity
                .status(429)
                .body(ApiResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .data(null)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ApiResponse<List<String>>> handlePasswordPolicyViolation(
            PasswordPolicyViolationException ex, WebRequest request) {
        logger.warn("Password policy violation: {} errors", ex.getErrors().size());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<List<String>>builder()
                        .success(false)
                        .message("Password does not meet policy requirements")
                        .data(ex.getErrors())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // ═════════════════════════════════════════════════════════════
    //  BUSINESS LOGIC EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailExists(
            EmailAlreadyExistsException ex, WebRequest request) {
        logger.warn("Email conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(PhoneNumberAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handlePhoneExists(
            PhoneNumberAlreadyExistsException ex, WebRequest request) {
        logger.warn("Phone conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateResource(
            DuplicateResourceException ex, WebRequest request) {
        logger.warn("Duplicate resource: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleOrderNotFound(
            OrderNotFoundException ex, WebRequest request) {
        logger.warn("Order not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AddressNotPresentException.class)
    public ResponseEntity<ApiResponse<Object>> handleAddressNotPresent(
            AddressNotPresentException ex, WebRequest request) {
        logger.warn("Address missing: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ═════════════════════════════════════════════════════════════
    //  ORDER & PAYMENT EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(OrderCancellationException.class)
    public ResponseEntity<ApiResponse<Object>> handleOrderCancellation(
            OrderCancellationException ex, WebRequest request) {
        logger.warn("Order cancellation failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidOrderStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidOrderStatus(
            InvalidOrderStatusException ex, WebRequest request) {
        logger.warn("Invalid order status: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handlePaymentProcessing(
            PaymentProcessingException ex, WebRequest request) {
        logger.error("Payment processing error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ═════════════════════════════════════════════════════════════
    //  VENDOR EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(VendorNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Object>> handleVendorNotVerified(
            VendorNotVerifiedException ex, WebRequest request) {
        logger.warn("Vendor not verified: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // ═════════════════════════════════════════════════════════════
    //  PRODUCT EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(ProductUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleProductUnavailable(
            ProductUnavailableException ex, WebRequest request) {
        logger.warn("Product unavailable: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleImageValidation(
            ImageValidationException ex, WebRequest request) {
        logger.warn("Image validation failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ═════════════════════════════════════════════════════════════
    //  VALIDATION EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<ValidationErrorDto>>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {

        List<ValidationErrorDto> errors = new ArrayList<>();

        // Field errors
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(ValidationErrorDto.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .rejectedValue(error.getRejectedValue())
                        .build())
        );

        // Global errors
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(ValidationErrorDto.builder()
                        .field(error.getObjectName())
                        .message(error.getDefaultMessage())
                        .rejectedValue(null)
                        .build())
        );

        logger.warn("Validation failed: {} errors", errors.size());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<List<ValidationErrorDto>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<ValidationErrorDto>>> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {

        List<ValidationErrorDto> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> {
                    String fieldName = violation.getPropertyPath().toString();
                    if (fieldName.contains(".")) {
                        fieldName = fieldName.substring(fieldName.lastIndexOf('.') + 1);
                    }
                    return ValidationErrorDto.builder()
                            .field(fieldName)
                            .message(violation.getMessage())
                            .rejectedValue(violation.getInvalidValue())
                            .build();
                })
                .collect(Collectors.toList());

        logger.warn("Constraint violation: {} errors", errors.size());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<List<ValidationErrorDto>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // ═════════════════════════════════════════════════════════════
    //  HTTP/PARSING EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParams(
            MissingServletRequestParameterException ex, WebRequest request) {
        String message = String.format("Missing required parameter: %s", ex.getParameterName());
        logger.warn(message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = String.format("Invalid value for parameter '%s': %s",
                ex.getName(), ex.getValue());
        logger.warn(message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidJson(
            HttpMessageNotReadableException ex, WebRequest request) {
        logger.warn("Invalid JSON body: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST,
                "Malformed or invalid JSON request body", request);
    }

    // ═════════════════════════════════════════════════════════════
    //  GENERAL EXCEPTIONS
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        logger.warn("Illegal argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        logger.warn("Illegal state: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Object>> handleIOException(
            IOException ex, WebRequest request) {
        logger.error("IO error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "File operation failed: " + ex.getMessage(), request);
    }

    // ═════════════════════════════════════════════════════════════
    //  CATCH-ALL
    // ═════════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(
            Exception ex, WebRequest request) {
        logger.error("Unhandled exception: ", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", request);
    }

    // ═════════════════════════════════════════════════════════════
    //  HELPER METHOD
    // ═════════════════════════════════════════════════════════════

    private ResponseEntity<ApiResponse<Object>> buildErrorResponse(
            HttpStatus status, String message, WebRequest request) {
        return ResponseEntity
                .status(status)
                .body(ApiResponse.builder()
                        .success(false)
                        .message(message)
                        .data(null)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}