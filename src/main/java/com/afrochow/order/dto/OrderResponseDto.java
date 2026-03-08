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
    private BigDecimal totalAmount;

    private OrderStatus status;
    private String specialInstructions;

    private String customerPublicId;
    private String customerName;
    private String vendorPublicId;
    private String vendorName;
    private String restaurantName;
    private AddressResponseDto deliveryAddress;

    private List<OrderLineResponseDto> orderLines;
    private PaymentResponseDto payment;

    private LocalDateTime orderTime;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime outForDeliveryAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime estimatedDeliveryTime;

    private Boolean canBeCancelled;
    private Boolean isCompleted;
    private Boolean isActive;

    private LocalDateTime updatedAt;
}