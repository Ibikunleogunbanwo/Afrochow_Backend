package com.afrochow.orderline.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineResponseDto {

    private Long orderLineId;
    private String productPublicId;
    private Integer quantity;
    private BigDecimal priceAtPurchase;
    private String productNameAtPurchase;
    private String productDescriptionAtPurchase;
    private String specialInstructions;
    private BigDecimal lineTotal;
    private String displayName;
}
