package com.afrochow.product.dto;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import com.afrochow.common.enums.ScheduleType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDto {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal price;

    private String imageUrl;

    @Builder.Default
    private Boolean available = true;

    @NotNull(message = "Preparation time is required")
    @Min(value = 1, message = "Preparation time must be at least 1 minute")
    @Max(value = 180, message = "Preparation time must not exceed 180 minutes")
    @Builder.Default
    private Integer preparationTimeMinutes = 45;

    @Min(value = 0, message = "Calories cannot be negative")
    @Max(value = 9999, message = "Calories must not exceed 9999")
    private Integer calories;

    private Boolean isVegetarian;
    private Boolean isVegan;
    private Boolean isGlutenFree;
    private Boolean isSpicy;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    /** SAME_DAY (default) or ADVANCE_ORDER */
    @Builder.Default
    private ScheduleType scheduleType = ScheduleType.SAME_DAY;

    /**
     * Required when scheduleType = ADVANCE_ORDER.
     * Minimum hours of advance notice required (1–168).
     */
    @Min(value = 1, message = "Advance notice must be at least 1 hour")
    @Max(value = 168, message = "Advance notice cannot exceed 168 hours (7 days)")
    private Integer advanceNoticeHours;
}
