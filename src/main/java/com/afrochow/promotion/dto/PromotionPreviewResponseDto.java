package com.afrochow.promotion.dto;

import com.afrochow.common.enums.PromotionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionPreviewResponseDto {

    private String promoCode;
    private String title;
    private String description;
    private PromotionType type;

    /** The raw discount value (e.g. 10 for 10% or $10 flat). */
    private BigDecimal value;

    /** The actual dollar amount discounted from the subtotal. */
    private BigDecimal discountAmount;

    /** Subtotal after discount — before delivery fee and tax. */
    private BigDecimal discountedSubtotal;
}
