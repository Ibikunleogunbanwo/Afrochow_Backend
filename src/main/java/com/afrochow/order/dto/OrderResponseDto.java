package com.afrochow.order.dto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.orderline.dto.OrderLineResponseDto;
import com.afrochow.payment.dto.PaymentResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDto {

    private String publicOrderId;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal tax;
    private BigDecimal discount;
    private String appliedPromoCode;
    private BigDecimal totalAmount;

    private OrderStatus status;
    private String statusLabel;
    private String specialInstructions;

    private String customerPublicId;
    private String customerName;
    private String vendorPublicId;
    private String vendorName;
    private String restaurantName;
    private AddressResponseDto deliveryAddress;

    private List<OrderLineResponseDto> orderLines;
    private PaymentResponseDto payment;

    private String fulfillmentType;
    private LocalDateTime requestedFulfillmentTime;

    private LocalDateTime orderTime;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime outForDeliveryAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime refundedAt;

    /** Who triggered the cancellation: CUSTOMER, VENDOR, VENDOR_POST_ACCEPT, SYSTEM, ADMIN. Null for non-cancelled orders. */
    private String cancelledBy;

    /** Human-readable cancellation reason supplied at cancellation time. Null if not applicable. */
    private String cancellationReason;
    private LocalDateTime estimatedDeliveryTime;

    private Boolean canBeCancelled;
    private Boolean isCompleted;
    private Boolean isActive;

    /**
     * When the vendor's acceptance window closes (orderTime + SLA).
     * Only populated for PENDING orders — null otherwise.
     */
    private LocalDateTime slaExpiresAt;

    /**
     * Seconds remaining until the SLA window closes.
     * Negative means the window has already passed.
     * Only populated for PENDING orders — null otherwise.
     */
    private Long slaRemainingSeconds;

    private LocalDateTime updatedAt;
}