package com.afrochow.customer.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDashboardDto {

    private Integer totalOrders;
    private Integer activeOrders;
    private Integer completedOrders;
    private BigDecimal totalSpent;
    private String favoriteRestaurant;
    private Integer savedAddresses;
}