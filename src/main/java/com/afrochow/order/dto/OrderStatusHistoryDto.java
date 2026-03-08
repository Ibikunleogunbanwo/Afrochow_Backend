package com.afrochow.order.dto;
import com.afrochow.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistoryDto {

    private OrderStatus status;
    private LocalDateTime timestamp;
    private String description;
}
