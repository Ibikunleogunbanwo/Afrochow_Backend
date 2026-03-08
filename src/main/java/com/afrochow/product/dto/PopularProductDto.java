package com.afrochow.product.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularProductDto {

    private String publicProductId;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Integer orderCount;
    private Double averageRating;
    private String restaurantName;
}