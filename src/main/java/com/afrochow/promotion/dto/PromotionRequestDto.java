package com.afrochow.promotion.dto;

import com.afrochow.common.enums.PromotionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequestDto {

    @NotBlank(message = "Promo code is required")
    @Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code can only contain letters, numbers, hyphens and underscores")
    private String code;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private String title;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Promotion type is required")
    private PromotionType type;

    /** Required for PERCENTAGE and FIXED_AMOUNT. Not needed for FREE_DELIVERY. */
    @DecimalMin(value = "0.00", message = "Value must be 0 or greater")
    private BigDecimal value;

    /**
     * Max discount cap — only relevant for PERCENTAGE type.
     * Send {@code null} or omit to mean "no cap". Sending {@code 0} is also
     * accepted and is normalised to "no cap" in the service layer.
     */
    @DecimalMin(value = "0.00", message = "Max discount amount cannot be negative")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0.00")
    private BigDecimal minimumOrderAmount;

    @Min(value = 1, message = "Usage limit must be at least 1")
    private Integer usageLimit;

    @Min(value = 1, message = "Per-user limit must be at least 1")
    private Integer perUserLimit;

    @NotBlank(message = "Start date is required")
    private String startDate;

    @NotBlank(message = "End date is required")
    private String endDate;

    private Boolean isActive;

    /** Optional — vendor public ID to restrict promo to one vendor. Null = global. */
    private String vendorPublicId;
}
