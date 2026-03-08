package com.afrochow.favorite.dto;

import com.afrochow.common.enums.FavoriteType;
import jakarta.validation.constraints.NotNull;
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
    private String vendorPublicId;

    /**
     * Public ID of the product (required if favoriteType = PRODUCT)
     */
    private String productPublicId;
}
