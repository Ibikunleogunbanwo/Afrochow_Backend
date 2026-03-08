package com.afrochow.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for single image upload
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Response for image upload operations")
public class ImageUploadResponseDto {

    @Schema(description = "Whether the upload was successful", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean success;

    @Schema(description = "Full URL of the uploaded image", example = "http://localhost:8080/api/images/logos/abc123.jpg")
    private String imageUrl;

    @Schema(description = "Success or error message", example = "Upload successful")
    private String message;


    /** Returns a “skipped” payload for optional files that were not supplied. */
    public static ImageUploadResponseDto skipped() {
        ImageUploadResponseDto dto = new ImageUploadResponseDto();
        dto.success = true;
        dto.message = "No file provided – upload skipped";
        dto.imageUrl = null;
        return dto;
    }
}