-- ===========================================================================
-- Afrochow Database Migration V6
-- Description: Create security-related tables (refresh tokens, password resets, login attempts, security events)
-- Author: Afrochow Development Team
-- Date: 2025-11-27
-- ===========================================================================

-- Refresh Token Table
CREATE TABLE IF NOT EXISTS refresh_token (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID token',
    expiry_date TIMESTAMP NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(45) COMMENT 'IPv4 or IPv6 address',
    user_agent TEXT COMMENT 'Browser/device information',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP NULL,

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_token (token),
    INDEX idx_expiry_date (expiry_date),
    INDEX idx_is_revoked (is_revoked),
    INDEX idx_user_active (user_id, is_revoked, expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='JWT refresh tokens table';

-- Password Reset Token Table
CREATE TABLE IF NOT EXISTS password_reset_token (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID token',
    expiry_date TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    ip_address VARCHAR(45) COMMENT 'IPv4 or IPv6 address',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_token (token),
    INDEX idx_expiry_date (expiry_date),
    INDEX idx_is_used (is_used),
    INDEX idx_token_valid (token, expiry_date, is_used)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Password reset tokens table';

-- Login Attempt Table
CREATE TABLE IF NOT EXISTS login_attempt (
    attempt_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    success BOOLEAN NOT NULL,
    ip_address VARCHAR(45) COMMENT 'IPv4 or IPv6 address',
    user_agent TEXT COMMENT 'Browser/device information',
    failure_reason VARCHAR(255),
    attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_email (email),
    INDEX idx_success (success),
    INDEX idx_attempt_time (attempt_time),
    INDEX idx_email_time (email, attempt_time),
    INDEX idx_email_success (email, success, attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Login attempt tracking table';

-- Security Event Table
CREATE TABLE IF NOT EXISTS security_event (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    email VARCHAR(255),
    type ENUM('LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGIN_LOCKED', 'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED', 'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED', 'TOKEN_REFRESH', 'LOGOUT', 'ADMIN_ACTION_PERFORMED', 'UNAUTHORIZED_ACCESS', 'SUSPICIOUS_ACTIVITY') NOT NULL,
    severity ENUM('INFO', 'WARNING', 'ERROR', 'CRITICAL') NOT NULL DEFAULT 'INFO',
    description TEXT,
    ip_address VARCHAR(45) COMMENT 'IPv4 or IPv6 address',
    user_agent TEXT COMMENT 'Browser/device information',
    metadata TEXT COMMENT 'Additional event data (JSON)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_security_event_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_email (email),
    INDEX idx_type (type),
    INDEX idx_severity (severity),
    INDEX idx_created_at (created_at),
    INDEX idx_user_type (user_id, type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Security event logging table';

-- ===========================================================================
-- End of V6 migration
-- ===========================================================================
