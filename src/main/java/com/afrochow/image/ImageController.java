package com.afrochow.image;

import com.afrochow.common.ApiResponse;
import com.afrochow.common.exceptions.ImageNotFoundException;
import com.afrochow.common.exceptions.ImageValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Controller for image upload and serving operations.
 *
 * <p>Public endpoints (no authentication required):
 * <ul>
 *   <li>GET /api/images/** — Serve images</li>
 *   <li>POST /api/images/upload/registration — Upload registration images</li>
 *   <li>POST /api/images/vendor_image_registration — Upload vendor documents</li>
 *   <li>POST /api/images/upload/user — Upload user-specific images</li>
 *   <li>DELETE /api/images — Delete image by URL</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Image upload and serving endpoints")
public class ImageController {

    private final ImageUploadService imageUploadService;

    // Only alphanumeric, underscore, hyphen — no slashes or dots for individual inputs
    private static final String SAFE_INPUT_PATTERN = "^[a-zA-Z0-9_-]+$";

    // Slashes and dots allowed for assembled relative paths only
    private static final String SAFE_PATH_PATTERN = "^[a-zA-Z0-9_./-]+$";

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    // ─── Serve image ──────────────────────────────────────────────────────────

    @GetMapping("/{category}/{filename:.+}")
    @Operation(summary = "Serve image file",
            description = "Retrieve and serve an image file by category and filename.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image retrieved successfully",
                    content = @Content(mediaType = "image/*")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Image not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Resource> serveImage(
            @Parameter(description = "Image category", required = true) @PathVariable String category,
            @Parameter(description = "Image filename", required = true) @PathVariable String filename) {

        try {
            String relativeFilePath = category + "/" + filename;
            byte[] imageBytes = imageUploadService.getImageBytes(relativeFilePath);
            String contentType = imageUploadService.getContentType(relativeFilePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(new ByteArrayResource(imageBytes));

        } catch (ImageNotFoundException e) {
            log.warn("Image not found: {}/{}", category, filename);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error serving image {}/{}: {}", category, filename, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ─── Upload registration image ────────────────────────────────────────────

    @PostMapping(value = "/upload/registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload registration image",
            description = "Upload a single image for the registration process. Accepts JPEG, PNG, GIF, and WebP up to 5MB.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image uploaded successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file or validation error",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Upload failed due to server error",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)))
    })
    public ResponseEntity<ApiResponse<ImageUploadResponseDto>> uploadRegistrationImage(
            @Parameter(description = "Image file (JPEG, PNG, GIF, WebP, max 5MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Image category", example = "registrations")
            @RequestParam("category") String category) {

        try {
            // ✅ Sanitize — breaks taint chain before reaching service
            String safeCategory = sanitizeInput(category, "category");

            String imageUrl = imageUploadService.uploadImageForRegistrationAndGetUrl(file, safeCategory);
            ImageUploadResponseDto dto = ImageUploadResponseDto.builder()
                    .success(true).imageUrl(imageUrl).message("Image uploaded successfully").build();
            return ResponseEntity.ok(ApiResponse.success("Image uploaded successfully", dto));

        } catch (ImageValidationException e) {
            log.warn("Registration image validation failed — category: {}, reason: {}", category, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            log.error("Registration image upload failed — category: {}, error: {}", category, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.internalError("Upload failed. Please try again."));
        }
    }

    // ─── Upload vendor registration documents ─────────────────────────────────

    @PostMapping(value = "/vendor_image_registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload vendor registration documents",
            description = "Upload logo and banner (required) plus optional business licence and profile image in one request.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All provided documents processed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Step4Response.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "At least one required file failed validation",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Step4Response.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Step4Response.class)))
    })
    public ResponseEntity<ApiResponse<Step4Response>> uploadDocuments(
            @Parameter(description = "Business licence image (optional)")
            @RequestPart(value = "businessLicense", required = false) MultipartFile license,
            @Parameter(description = "Logo image (required)", required = true)
            @RequestPart("logoFile") MultipartFile logo,
            @Parameter(description = "Banner image (required)", required = true)
            @RequestPart("bannerFile") MultipartFile banner,
            @Parameter(description = "Profile image (optional)")
            @RequestPart(value = "profileImage", required = false) MultipartFile profile) {

        ImageUploadResponseDto licenseResponse = handleOptional(license, "licenses");
        ImageUploadResponseDto logoResponse    = uploadSingleFile(logo,    "logos");
        ImageUploadResponseDto bannerResponse  = uploadSingleFile(banner,  "banners");
        ImageUploadResponseDto profileResponse = handleOptional(profile,   "profiles");

        Step4Response response = Step4Response.builder()
                .businessLicense(licenseResponse).logo(logoResponse)
                .banner(bannerResponse).profileImage(profileResponse)
                .build();

        boolean allOk = Stream.of(licenseResponse, logoResponse, bannerResponse, profileResponse)
                .allMatch(ImageUploadResponseDto::isSuccess);

        if (allOk) {
            return ResponseEntity.ok(ApiResponse.success("Documents uploaded successfully", response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.<Step4Response>builder()
                    .success(false)
                    .message("One or more files failed validation")
                    .errorCode("BAD_REQUEST")
                    .data(response)
                    .build());
        }
    }

    // ─── Upload user image ────────────────────────────────────────────────────

    @PostMapping("/upload/user")
    @Operation(summary = "Upload user image with userId as filename",
            description = "Upload an image using the user's public ID as the filename. Overwrites any existing image for this user.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image uploaded successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file, category, or userId",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Upload failed due to server error",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponseDto.class)))
    })
    public ResponseEntity<ApiResponse<ImageUploadResponseDto>> uploadUserImage(
            @Parameter(description = "Image file (JPEG, PNG, GIF, WebP, max 5MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Image category (e.g. profiles, vendors)", required = true)
            @RequestParam("category") String category,
            @Parameter(description = "User's public ID", required = true)
            @RequestParam("userId") String publicUserId) {

        try {
            // ✅ Sanitize both — breaks taint chain before reaching service
            String safeCategory = sanitizeInput(category, "category");
            String safeUserId   = sanitizeInput(publicUserId, "userId");

            String imageUrl = imageUploadService.uploadImageAndGetUrl(file, safeCategory, safeUserId);
            ImageUploadResponseDto dto = ImageUploadResponseDto.builder()
                    .success(true).imageUrl(imageUrl).message("Image uploaded successfully").build();
            return ResponseEntity.ok(ApiResponse.success("Image uploaded successfully", dto));

        } catch (ImageValidationException e) {
            log.warn("User image validation failed — category: {}, userId: {}, reason: {}", category, publicUserId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            log.error("User image upload failed — category: {}, userId: {}, error: {}", category, publicUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.internalError("Upload failed. Please try again."));
        }
    }

    // ─── Delete image ─────────────────────────────────────────────────────────

    @DeleteMapping
    @Operation(summary = "Delete image by URL",
            description = "Delete an image file using its full URL. The URL must belong to this server.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or external image URL"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Image not found")
    })
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @Parameter(description = "Full image URL to delete", required = true)
            @RequestParam("imageUrl") String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("Image URL is required"));
        }

        String prefix1 = appUrl + "/api/images/";
        String prefix2 = appUrl + "/images/";

        String relativePath;
        if (imageUrl.startsWith(prefix1)) {
            relativePath = imageUrl.substring(prefix1.length());
        } else if (imageUrl.startsWith(prefix2)) {
            relativePath = imageUrl.substring(prefix2.length());
        } else {
            log.warn("Delete rejected — URL does not match server origin: {}", imageUrl);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("Invalid image URL — must be a URL served by this application"));
        }

        if (relativePath.contains("..") || relativePath.contains("//")) {
            log.warn("Delete rejected — path traversal attempt detected: {}", relativePath);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("Invalid image path"));
        }

        if (!relativePath.matches(SAFE_PATH_PATTERN)) {
            log.warn("Delete rejected — path contains disallowed characters: {}", relativePath);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("Invalid image path"));
        }

        try {
            imageUploadService.deleteImage(relativePath);
            log.info("Image deleted successfully: {}", relativePath);
            return ResponseEntity.ok(ApiResponse.success("Image deleted successfully"));

        } catch (SecurityException e) {
            log.warn("Delete rejected — security exception for path: {}", relativePath);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("Invalid image path"));
        } catch (Exception e) {
            log.error("Delete failed — path: {}, error: {}", relativePath, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.internalError("Failed to delete image"));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Validates and sanitizes a single user-controlled input.
     * Throws {@link ImageValidationException} if the value is blank or contains
     * disallowed characters. Returns a new string stripped of any unsafe characters
     * to break the taint chain tracked by static analysis tools.
     */
    private String sanitizeInput(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ImageValidationException(fieldName + " is required");
        }
        if (!value.matches(SAFE_INPUT_PATTERN)) {
            throw new ImageValidationException(
                    "Invalid " + fieldName + " — only alphanumeric, underscore and hyphen allowed");
        }
        // ✅ Return a freshly built string — breaks the taint chain from
        // the HTTP request parameter to the file system sink in the service
        return value.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private ImageUploadResponseDto handleOptional(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return ImageUploadResponseDto.skipped();
        }
        return uploadSingleFile(file, folder);
    }

    private ImageUploadResponseDto uploadSingleFile(MultipartFile file, String category) {
        try {
            String imageUrl = imageUploadService.uploadImageForRegistrationAndGetUrl(file, category);
            return ImageUploadResponseDto.builder()
                    .success(true).imageUrl(imageUrl).message("Image uploaded successfully").build();
        } catch (ImageValidationException e) {
            log.warn("File validation failed — category: {}, reason: {}", category, e.getMessage());
            return ImageUploadResponseDto.builder()
                    .success(false).imageUrl(null).message(e.getMessage()).build();
        } catch (Exception e) {
            log.error("File upload failed — category: {}, error: {}", category, e.getMessage());
            return ImageUploadResponseDto.builder()
                    .success(false).imageUrl(null).message("Upload failed").build();
        }
    }
}