package com.afrochow.customer.dto;

import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdateRequestDto {

    private String email;
    private String profileImageUrl;
    private String firstName;
    private String lastName;
    private String phone;
    private Role role;
    private String defaultDeliveryInstructions;
    private PaymentMethod paymentMethod;
    private Integer loyaltyPoints;

    /**
     * Optional helper: ensure role is CUSTOMER if provided
     */
    public boolean isRoleCustomer() {
        return role == null || role == Role.CUSTOMER;
    }
}
