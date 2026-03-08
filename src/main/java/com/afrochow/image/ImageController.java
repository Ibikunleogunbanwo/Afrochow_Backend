package com.afrochow.image;

import com.afrochow.common.exceptions.ImageNotFoundException;
import com.afrochow.common.exceptions.ImageValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Stream;

/**
 * Controller for image upload and serving operations
 *
 * PUBLIC ENDPOINTS (no authentication required):
 * - GET /api/images/** - Serve images
 * - POST /api/images/upload/registration - Upload registration images
 * - POST /api/images/step-4 - Upload multiple documents for step 4
 * - POST /api/images/upload/user - Upload user-specific images
 */
@Slf4j
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Image upload and serving endpoints")
public class ImageController {

    private final ImageUploadService imageUploadService;

    /**
     * Serve image file by category and filename
     *
     * @param category Image category (e.g., "vendors", "products", "customers")
     * @param filename Image filename
     * @return Image file or 404 if not found
     */
    @GetMapping("/{category}/{filename:.+}")
    @Operation(
            summary = "Serve image file",
            description = "Retrieve and serve an image file by category and filename. Returns the image with appropriate content type and caching headers."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Image retrieved successfully",
                    content = @Content(mediaType = "image/*")
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Image not found"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })


    public ResponseEntity<Resource> serveImage(
            @Parameter(description = "Image category (e.g., vendors, products, customers)", required = true)
            @PathVariable String category,
            @Parameter(description = "Image filename", required = true)
            @PathVariable String filename) {

        try {
            String relativeFilePath = category + "/" + filename;
            byte[] imageBytes = imageUploadService.getImageBytes(relativeFilePath);
            String contentType = imageUploadService.getContentType(relativeFilePath);

            ByteArrayResource resource = new ByteArrayResource(imageBytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(resource);

        } catch (ImageNotFoundException e) {
            log.warn("Image not found: {}/{}", category, filename);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error serving image: {}/{}", category, filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



    /**
     * Upload a single image for registration process
     *
     * @param file     Image file to upload
     * @param category Optional category (defaults to "registrations")
     * @return Image upload response DTO
     */
    @PostMapping(value = "/upload/registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload registration image",
            description = "Upload a single image for the registration process. Generates a unique UUID filename. Accepts JPEG, PNG, GIF, and WebP formats up to 5MB."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Image uploaded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid file or validation error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Upload failed due to server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)
                    )
            )
    })
    public ResponseEntity<ImageUploadResponseDto> uploadRegistrationImage(
            @Parameter(description = "Image file (JPEG, PNG, GIF, WebP, max 5MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Image category", example = "registrations")
            @RequestParam(value = "category", defaultValue = "registrations") String category) {

        try {
            String imageUrl = imageUploadService.uploadImageForRegistrationAndGetUrl(file, category);

            ImageUploadResponseDto response = ImageUploadResponseDto.builder()
                    .success(true)
                    .imageUrl(imageUrl)
                    .message("Upload successful")
                    .build();

            return ResponseEntity.ok(response);

        } catch (ImageValidationException e) {
            log.warn("Image validation failed: {}", e.getMessage());

            ImageUploadResponseDto response = ImageUploadResponseDto.builder()
                    .success(false)
                    .imageUrl(null)
                    .message(e.getMessage())
                    .build();

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error("Image upload failed", e);

            ImageUploadResponseDto response = ImageUploadResponseDto.builder()
                    .success(false)
                    .imageUrl(null)
                    .message("Upload failed. Please try again.")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Upload documents for registration step 4.
     * Only logo and banner are mandatory; license and profile image are optional.
     *
     * @param license Business-license image (optional)
     * @param logo    Logo image (required)
     * @param banner  Banner image (required)
     * @param profile Profile image (optional)
     * @return Step-4 response with upload results for every field
     */
    @PostMapping(value = "/vendor_image_registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload documents for step 4 (logo & banner required, license & profile optional)",
            description = "Upload logo and banner (required) plus optional business-license and profile image in one request. Skipped optional files are marked as successful with null URLs."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All provided documents processed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Step4Response.class))),
            @ApiResponse(responseCode = "400", description = "At least one required file failed validation",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Step4Response.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Step4Response.class)))
    })
    public ResponseEntity<Step4Response> uploadDocuments(
            @Parameter(description = "Business-license image (optional)", required = false)
            @RequestPart(value = "businessLicense", required = false) MultipartFile license,

            @Parameter(description = "Logo image (required)", required = true)
            @RequestPart("logoFile") MultipartFile logo,

            @Parameter(description = "Banner image (required)", required = true)
            @RequestPart("bannerFile") MultipartFile banner,

            @Parameter(description = "Profile image (optional)", required = false)
            @RequestPart(value = "profileImage", required = false) MultipartFile profile) {

        ImageUploadResponseDto licenseResponse = handleOptional(license, "licenses");
        ImageUploadResponseDto logoResponse    = uploadSingleFile(logo,    "logos");
        ImageUploadResponseDto bannerResponse  = uploadSingleFile(banner,  "banners");
        ImageUploadResponseDto profileResponse = handleOptional(profile,   "profiles");

        Step4Response response = Step4Response.builder()
                .businessLicense(licenseResponse)
                .logo(logoResponse)
                .banner(bannerResponse)
                .profileImage(profileResponse)
                .build();

        boolean allOk = Stream.of(licenseResponse, logoResponse, bannerResponse, profileResponse)
                .allMatch(ImageUploadResponseDto::isSuccess);

        return allOk ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }

    /* ---------- helper ---------- */
    private ImageUploadResponseDto handleOptional(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return ImageUploadResponseDto.skipped();
        }
        return uploadSingleFile(file, folder);
    }
    /**
     * Upload image with user ID as filename (overwrites existing)
     *
     * @param file         Image file to upload
     * @param category     Image category
     * @param publicUserId User's public ID (used as filename)
     * @return Image upload response DTO
     */
    @PostMapping("/upload/user")
    @Operation(
            summary = "Upload user image with userId as filename",
            description = "Upload an image using the user's public ID as the filename. This will overwrite any existing image for this user in the specified category. Old images with different extensions are automatically deleted."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Image uploaded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid file, category, or userId",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Upload failed due to server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)
                    )
            )
    })
    public ResponseEntity<ImageUploadResponseDto> uploadUserImage(
            @Parameter(description = "Image file (JPEG, PNG, GIF, WebP, max 5MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Image category (e.g., profiles, vendors)", required = true)
            @RequestParam("category") String category,
            @Parameter(description = "User's public ID (alphanumeric, underscore, hyphen only)", required = true)
            @RequestParam("userId") String publicUserId) {

        try {
            String imageUrl = imageUploadService.uploadImageAndGetUrl(file, category, publicUserId);

            ImageUploadResponseDto response = ImageUploadResponseDto.builder()
                    .success(true)
                    .imageUrl(imageUrl)
                    .message("Upload successful")
                    .build();

            return ResponseEntity.ok(response);

        } catch (ImageValidationException e) {
            log.warn("Image validation failed: {}", e.getMessage());

            ImageUploadResponseDto response = ImageUploadResponseDto.builder()
                    .success(false)
                    .imageUrl(null)
                    .message(e.getMessage())
                    .build();

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error("User image upload failed", e);

            ImageUploadResponseDto response = ImageUploadResponseDto.builder()
                    .success(false)
                    .imageUrl(null)
                    .message("Upload failed. Please try again.")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Helper method to upload a single file and return DTO
     */
    private ImageUploadResponseDto uploadSingleFile(MultipartFile file, String category) {
        try {
            String imageUrl = imageUploadService.uploadImageForRegistrationAndGetUrl(file, category);
            return ImageUploadResponseDto.builder()
                    .success(true)
                    .imageUrl(imageUrl)
                    .message("Upload successful")
                    .build();
        } catch (ImageValidationException e) {
            log.warn("File validation failed for {}: {}", category, e.getMessage());
            return ImageUploadResponseDto.builder()
                    .success(false)
                    .imageUrl(null)
                    .message(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("File upload failed for {}", category, e);
            return ImageUploadResponseDto.builder()
                    .success(false)
                    .imageUrl(null)
                    .message("Upload failed")
                    .build();
        }
    }
}