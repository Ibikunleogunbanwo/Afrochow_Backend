package com.afrochow.promotion.dto;

import com.afrochow.common.enums.PromotionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResponseDto {

    private String publicPromotionId;
    private String code;
    private String title;
    private String description;
    private PromotionType type;
    private BigDecimal value;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minimumOrderAmount;
    private Integer usageLimit;
    private Integer perUserLimit;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isActive;
    private Boolean isCurrentlyActive;

    // Vendor restriction (null = global)
    private String vendorPublicId;
    private String vendorName;

    // Usage stats (admin only)
    private Long totalUsageCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
