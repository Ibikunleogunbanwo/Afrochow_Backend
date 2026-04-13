package com.afrochow.vendor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadata submitted alongside a food handling certificate file upload.
 * The actual certificate file is submitted as a multipart form field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Metadata for a food handling certificate upload")
public class FoodHandlingCertUploadRequestDto {

    @NotBlank(message = "Certificate number is required")
    @Size(max = 100, message = "Certificate number must not exceed 100 characters")
    @Schema(description = "Certificate number as printed on the document", example = "FS-BC-2024-123456")
    private String certNumber;

    @NotBlank(message = "Issuing body is required")
    @Size(max = 150, message = "Issuing body must not exceed 150 characters")
    @Schema(
        description = "The body that issued the certificate. Province-specific in Canada.",
        example = "FoodSafe BC",
        allowableValues = {"FoodSafe BC", "Manitoba Food Handler", "Ontario Food Handler",
                           "Saskatchewan Food Safety", "Alberta Food Safety", "CFIA", "Other"}
    )
    private String issuingBody;

    @Future(message = "Certificate expiry must be a future date")
    @Schema(description = "Expiry date of the certificate (ISO 8601). Most Canadian certs expire after 5 years.",
            example = "2029-06-15T00:00:00")
    private LocalDateTime certExpiry;
}
