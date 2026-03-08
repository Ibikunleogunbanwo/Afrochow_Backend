package com.afrochow.common.exceptions;
import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

import java.util.Date;

/**
 * JWT Expired Token Exception
 *
 * Thrown when a JWT token has expired.
 * Provides additional context about when the token expired
 * and when it was issued.
 */
@Getter
public class JwtExpiredTokenException extends AuthenticationException {

    private final Date expiredAt;
    private final Date issuedAt;

    public JwtExpiredTokenException(String message, Date expiredAt, Date issuedAt) {
        super(message);
        this.expiredAt = expiredAt;
        this.issuedAt = issuedAt;
    }

    public JwtExpiredTokenException(String message, Throwable cause, Date expiredAt, Date issuedAt) {
        super(message, cause);
        this.expiredAt = expiredAt;
        this.issuedAt = issuedAt;
    }

    @Override
    public String getMessage() {
        return String.format("%s (Issued: %s, Expired: %s)",
                super.getMessage(),
                issuedAt != null ? issuedAt : "unknown",
                expiredAt != null ? expiredAt : "unknown"
        );
    }
}