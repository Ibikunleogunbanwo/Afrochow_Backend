package com.afrochow.review.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDto {

    private Long reviewId;

    private String userPublicId;
    private String userName;

    private String vendorPublicId;
    private String restaurantName;

    private String productPublicId;
    private String productName;

    private Integer rating;
    private String comment;
    private String starRating;

    private Integer helpfulCount;
    private Boolean isVisible;

    private Boolean isProductReview;
    private Boolean isVendorReview;
    private Boolean canBeEdited;

    private String orderPublicId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}