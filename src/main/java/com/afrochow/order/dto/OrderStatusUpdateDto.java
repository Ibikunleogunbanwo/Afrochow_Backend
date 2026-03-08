package com.afrochow.order.dto;

import com.afrochow.common.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateDto {

    @NotNull(message = "Order status is required")
    private OrderStatus status;
}
