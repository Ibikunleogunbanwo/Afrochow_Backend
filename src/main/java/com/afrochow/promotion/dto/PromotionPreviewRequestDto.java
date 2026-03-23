package com.afrochow.promotion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionPreviewRequestDto {

    @NotBlank(message = "Promo code is required")
    private String promoCode;

    @NotBlank(message = "Vendor ID is required")
    private String vendorPublicId;

    @NotNull(message = "Subtotal is required")
    @DecimalMin(value = "0.01", message = "Subtotal must be greater than 0")
    private BigDecimal subtotal;
}
