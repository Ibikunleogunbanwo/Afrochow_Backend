package com.afrochow.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for step 4 document uploads
 * Contains upload results for business license, logo, banner, and profile image
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Response containing upload results for all step 4 documents")
public class Step4Response {

    @JsonProperty("businessLicense")
    @Schema(description = "Business license upload result", requiredMode = Schema.RequiredMode.REQUIRED)
    private ImageUploadResponseDto businessLicense;

    @JsonProperty("logo")
    @Schema(description = "Logo upload result", requiredMode = Schema.RequiredMode.REQUIRED)
    private ImageUploadResponseDto logo;

    @JsonProperty("banner")
    @Schema(description = "Banner upload result", requiredMode = Schema.RequiredMode.REQUIRED)
    private ImageUploadResponseDto banner;

    @JsonProperty("profileImage")
    @Schema(description = "Profile image upload result", requiredMode = Schema.RequiredMode.REQUIRED)
    private ImageUploadResponseDto profileImage;

    /**
     * Check if all uploads were successful
     * @return true if all four uploads succeeded, false otherwise
     */
    public boolean isAllSuccessful() {
        return businessLicense != null && businessLicense.isSuccess() &&
                logo != null && logo.isSuccess() &&
                banner != null && banner.isSuccess() &&
                profileImage != null && profileImage.isSuccess();
    }

    /**
     * Get count of successful uploads
     * @return number of successful uploads (0-4)
     */
    public int getSuccessCount() {
        int count = 0;
        if (businessLicense != null && businessLicense.isSuccess()) count++;
        if (logo != null && logo.isSuccess()) count++;
        if (banner != null && banner.isSuccess()) count++;
        if (profileImage != null && profileImage.isSuccess()) count++;
        return count;
    }
}