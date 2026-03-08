package com.afrochow.admin.dto;
import com.afrochow.common.enums.AdminAccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
