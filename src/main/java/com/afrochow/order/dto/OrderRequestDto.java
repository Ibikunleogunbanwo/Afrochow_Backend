package com.afrochow.order.dto;

import com.afrochow.orderline.dto.OrderLineRequestDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDto {

    @NotBlank(message = "Vendor ID is required")
    private String vendorPublicId;

    /**
     * DELIVERY or PICKUP.
     * Determines whether deliveryAddressPublicId is required
     * and which province is used for tax calculation.
     */
    @NotBlank(message = "Fulfillment type is required")
    @Pattern(regexp = "DELIVERY|PICKUP", message = "fulfillmentType must be DELIVERY or PICKUP")
    private String fulfillmentType;

    /**
     * Required when fulfillmentType is DELIVERY.
     * publicAddressId of the customer's saved delivery address.
     */
    private String deliveryAddressPublicId;

    /**
     * Stripe payment method token — created client-side via stripe.createPaymentMethod().
     * Raw card details never reach this backend.
     */
    @NotBlank(message = "Payment method is required")
    private String paymentMethodId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderLineRequestDto> orderLines;

    @Size(max = 500, message = "Special instructions must not exceed 500 characters")
    private String specialInstructions;

    /** Optional promo code to apply a discount to this order. */
    @Size(max = 50)
    private String promoCode;

    /**
     * Required when the cart contains any ADVANCE_ORDER product.
     * The customer's desired fulfilment date/time (ISO 8601: "yyyy-MM-dd'T'HH:mm:ss").
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime requestedFulfillmentTime;
}