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
    private LocalDateTime requestedFulfillmentTime;
    private Boolean canBeCancelled;

    /**
     * Whether this order was placed against a demo/showroom vendor.
     *
     * <p>Derived from the vendor rather than stored on the order, because nothing
     * seeds orders — no migration or seeder inserts into {@code orders}, so every
     * order in the system is genuinely customer-placed and an order-level seed flag
     * would always be false. What IS worth surfacing is an order against a showroom
     * vendor: those have placeholder owners who cannot actually fulfil it, so it
     * signals an operational problem rather than demo noise.
     */
    private Boolean vendorIsSeedData;
}
