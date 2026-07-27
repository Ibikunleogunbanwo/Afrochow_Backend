package com.afrochow.favorite.dto;

import com.afrochow.common.enums.FavoriteType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for adding/removing favorites
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteRequestDto {

    @NotNull(message = "Favorite type is required")
    private FavoriteType favoriteType;

    /**
     * Public ID of the vendor (required if favoriteType = VENDOR)
     */
    @Size(max = 64, message = "Vendor ID must not exceed 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Vendor ID contains invalid characters")
    private String vendorPublicId;

    /**
     * Public ID of the product (required if favoriteType = PRODUCT)
     */
    @Size(max = 64, message = "Product ID must not exceed 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Product ID contains invalid characters")
    private String productPublicId;
}
