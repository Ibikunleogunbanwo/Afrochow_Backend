package com.afrochow.admin.dto;

import com.afrochow.auth.dto.BaseRegistrationRequestDto;
import com.afrochow.common.enums.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request to create a new admin account (POST /auth/register/admin, SUPERADMIN-only).
 *
 * <p>Deliberately does NOT accept {@code accessLevel} or any {@code can*}
 * permission flag. Those used to be settable here, which let a requesting
 * SUPERADMIN create an account that displayed as "Super Admin — full access"
 * while the account was, underneath, an ordinary ADMIN — those fields were
 * never actually read by any authorization check. Every new admin created
 * through this endpoint is a plain ADMIN; real SUPERADMIN status is granted
 * afterward, explicitly, via the promote endpoint in SuperAdminController.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileRequestDto extends BaseRegistrationRequestDto {

    // ========== ADMIN INFORMATION ==========

    // ========== REQUIRED USERNAME (FOR BASE CLASS) ==========
    @Schema(description = "Username (optional - auto-generated if not provided)")
    private String username;

    @Override
    public String getUsername() {
        return this.username;
    }

    @NotNull(message = "Department is required")
    private Department department;

    @Size(max = 50, message = "Employee ID must not exceed 50 characters")
    private String employeeId;
}
