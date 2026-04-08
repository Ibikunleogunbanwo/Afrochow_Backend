package com.afrochow.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for a vendor cancelling an order they have already accepted.
 *
 * This path is available when the order is CONFIRMED or PREPARING — i.e. after
 * the Stripe payment has been captured but before the food is ready.  A full
 * refund is issued to the customer automatically.
 */
@Data
public class VendorCancelFulfillmentRequestDto {

    /**
     * Why the vendor cannot fulfil the order.
     * Shown to the customer in the cancellation notification and stored for
     * admin/reporting purposes.
     *
     * Examples: "Out of stock", "Kitchen equipment failure", "Closing early today"
     */
    @NotBlank(message = "A reason must be provided when cancelling an accepted order")
    @Size(max = 300, message = "Reason must not exceed 300 characters")
    private String reason;
}
