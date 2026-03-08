package com.afrochow.admin.dto;
import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileResponseDto {

    private String publicUserId;
    private Department department;
    private AdminAccessLevel accessLevel;
    private String employeeId;
    private Boolean isSuperAdmin;
    private Boolean hasFullAccess;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String profileImageUrl;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}