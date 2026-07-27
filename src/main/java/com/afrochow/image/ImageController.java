package com.afrochow.image;

import com.afrochow.common.ApiResponse;
import com.afrochow.common.exceptions.ImageNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * Controller for image serving and cleanup.
 *
 * <p>Public endpoints (no authentication required):
 * <ul>
 *   <li>GET /api/images/** — Serve images (local dev only)</li>
 *   <li>DELETE /api/images — Delete image by URL</li>
 * </ul>
 *
 * <p>Upload endpoints ({@code /upload/registration}, {@code /vendor_image_registration},
 * {@code /upload/user}) were removed as dead code — the frontend uploads directly to
 * Cloudinary client-side with an unsigned preset, so nothing calls these anymore.
 * Deletion still goes through the backend since it requires the Cloudinary API secret.
 */
@Slf4j
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Image upload and serving endpoints")
public class ImageController {

    private final ImageUploadService imageUploadService;

    // ─── Serve image (dev / local filesystem only) ────────────────────────────

    @GetMapping("/{category}/{filename:.+}")
    @Operation(summary = "Serve image file (local dev only)",
            description = "Retrieve and serve a locally stored image. In production, images are served directly from Cloudinary.")
    public ResponseEntity<Resource> serveImage(
            @PathVariable String category,
            @PathVariable String filename) {

        try {
            String relativeFilePath = category + "/" + filename;
            byte[] imageBytes  = imageUploadService.getImageBytes(relativeFilePath);
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

        try {
            imageUploadService.deleteImage(imageUrl);
            log.info("Image deleted successfully: {}", imageUrl);
            return ResponseEntity.ok(ApiResponse.success("Image deleted successfully"));

        } catch (Exception e) {
            log.error("Delete failed — url: {}, error: {}", imageUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.internalError("Failed to delete image"));
        }
    }

}