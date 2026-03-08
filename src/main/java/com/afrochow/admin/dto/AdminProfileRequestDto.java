package com.afrochow.admin.dto;

import com.afrochow.auth.dto.BaseRegistrationRequest;
import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileRequestDto extends BaseRegistrationRequest {

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

    @Builder.Default
    private AdminAccessLevel accessLevel = AdminAccessLevel.MODERATOR;

    @Size(max = 50, message = "Employee ID must not exceed 50 characters")
    private String employeeId;

    // ========== PERMISSIONS ==========
    @Builder.Default
    private Boolean canVerifyVendors = false;

    @Builder.Default
    private Boolean canManageUsers = false;

    @Builder.Default
    private Boolean canViewReports = false;

    @Builder.Default
    private Boolean canManagePayments = false;

    @Builder.Default
    private Boolean canManageCategories = false;

    @Builder.Default
    private Boolean canResolveDisputes = false;
}
