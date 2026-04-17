package com.afrochow.customer.dto;

import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.validation.CanadianPhone;
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
    private String firstName;
    private String lastName;
    @CanadianPhone
    private String phone;
    private String defaultDeliveryInstructions;
    private PaymentMethod paymentMethod;
}
