package com.afrochow.common.exceptions;

import com.afrochow.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the mapping between the thrown exception type and the handler's method
 * parameter type. Historically {@code handleInvalidTokenException} was annotated
 * with {@code @ExceptionHandler(InvalidTokenException.class)} but took a
 * {@code JwtExpiredTokenException} parameter — the two are sibling classes, so
 * dispatching a real {@code InvalidTokenException} through Spring MVC would have
 * thrown at invocation time instead of producing a 401.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInvalidTokenException_returns401WithGenericMessage() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleInvalidTokenException(new InvalidTokenException("Invalid or expired token"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isNotBlank();
    }

    @Test
    void handleInvalidTokenException_doesNotLeakInternalReason() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleInvalidTokenException(new InvalidTokenException("token-abc is already used"), null);

        assertThat(response.getBody().getMessage()).doesNotContain("token-abc");
    }
}
