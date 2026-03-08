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
public class ProductUpdateRequestDto {

    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Boolean available;
    private Integer preparationTimeMinutes;
    private Integer calories;
    private Boolean isVegetarian;
    private Boolean isVegan;
    private Boolean isGlutenFree;
    private Boolean isSpicy;
    private Long categoryId;
}
