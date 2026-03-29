package com.afrochow.order.dto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.enums.OrderStatus;
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
public class OrderSummaryResponseDto {

    private String publicOrderId;
    private String vendorPublicId;
    private String vendorName;
    private String restaurantName;
    private AddressResponseDto deliveryAddress;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String statusLabel;
    private LocalDateTime orderTime;
    private LocalDateTime estimatedDeliveryTime;
    private Integer itemCount;
    private List<String> itemNames;
    private String fulfillmentType;
    private Boolean canBeCancelled;
}
