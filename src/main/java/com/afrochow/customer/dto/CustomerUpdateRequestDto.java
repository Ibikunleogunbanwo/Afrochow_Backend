package com.afrochow.customer.dto;

import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.validation.CanadianPhone;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer-writable profile fields.
 *
 * <p>This DTO intentionally does NOT expose:
 * <ul>
 *   <li>{@code email} — email changes go through a verified flow in UserController
 *       so a stolen session cannot silently re-route account recovery.</li>
 *   <li>{@code role} — role transitions are an admin concern, never user-writable.</li>
 *   <li>{@code loyaltyPoints} — awarded by the server, never trusted from the client.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdateRequestDto {

    private String profileImageUrl;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @CanadianPhone
    private String phone;

    @Size(max = 500, message = "Delivery instructions must not exceed 500 characters")
    private String defaultDeliveryInstructions;

    private PaymentMethod paymentMethod;
}
