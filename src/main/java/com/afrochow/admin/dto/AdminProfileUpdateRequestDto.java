package com.afrochow.admin.dto;
import com.afrochow.common.enums.AdminAccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NOTE: accessLevel/employeeId/permission flags are intentionally NOT applied by
 * {@link com.afrochow.admin.service.AdminProfileService#updateProfile}, even
 * though they're still accepted here for backward request-shape compatibility.
 * This DTO backs a self-service "update my own profile" endpoint with no
 * additional privilege check, so allowing it to set permission fields would let
 * any admin grant themselves elevated access. Only department is honored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileUpdateRequestDto {


    private String department;

    private AdminAccessLevel accessLevel;

    private String employeeId;

    private Boolean canVerifyVendors;
    private Boolean canManageUsers;
    private Boolean canViewReports;
    private Boolean canManagePayments;
    private Boolean canManageCategories;
    private Boolean canResolveDisputes;
}
