package com.afrochow.order.dto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.enums.OrderStatus;
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
public class OrderTrackingDto {

    private String publicOrderId;
    private OrderStatus currentStatus;
    private String restaurantName;
    private AddressResponseDto deliveryAddress;
    private LocalDateTime estimatedDeliveryTime;
    private List<OrderStatusHistoryDto> statusHistory;
}