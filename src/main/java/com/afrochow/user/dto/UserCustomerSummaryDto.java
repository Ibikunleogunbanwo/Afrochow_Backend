package com.afrochow.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class UserCustomerSummaryDto {
    private String publicUserId;
    private String firstName;
    private String email;
    private String username;
    private String lastName;
    private String phone;
    private String role;
    private Boolean isActive;
}
