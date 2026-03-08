package com.afrochow.auth.dto;
import com.afrochow.common.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {

    private String token;
    private String tokenType;
    private String publicUserId;
    private String email;
    private String fullName;
    private Role role;
    private Object profile; // Can be CustomerProfileResponseDto, VendorProfileResponseDto, or AdminProfileResponseDto
}