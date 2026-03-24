package com.afrochow.image;

import com.afrochow.common.exceptions.ImageValidationException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final Cloudinary cloudinary;

    @Value("${app.upload.max-file-size:5242880}")
    private long maxFileSize;

    // Whitelist of allowed categories (used as Cloudinary folders)
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "registrations", "licenses", "logos", "banners", "profiles",
            "vendors", "VendorLogo", "VendorBanner", "VendorProfileImage", "products", "customers",
            "documents", "CustomerProfileImage", "admins",
            "AdminProfileImage", "VendorBusinessLicense", "vendors/banners", "vendors/logos"
    );

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    private static final int MAX_WIDTH        = 10000;
    private static final int MAX_HEIGHT       = 10000;
    private static final long MAX_PIXELS      = 50_000_000L;
    private static final int MAX_USERID_LENGTH = 100;

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Upload image to Cloudinary with a UUID public ID.
     * Returns the full Cloudinary secure URL.
     */
    public String uploadImageForRegistration(MultipartFile file, String category) throws IOException {
        validateCategory(category);
        byte[] fileBytes = validateFileAndGetBytes(file);
        String publicId  = UUID.randomUUID().toString();
        return uploadToCloudinary(fileBytes, category, publicId, false);
    }

    /**
     * Upload image to Cloudinary and return secure URL (same as uploadImageForRegistration).
     */
    public String uploadImageForRegistrationAndGetUrl(MultipartFile file, String category) throws IOException {
        return uploadImageForRegistration(file, category);
    }

    /**
     * Upload image using publicUserId as the Cloudinary public ID (overwrites existing).
     * Returns the full Cloudinary secure URL.
     */
    public String uploadImage(MultipartFile file, String category, String publicUserId) throws IOException {
        validateCategory(category);
        validateUserId(publicUserId);
        byte[] fileBytes = validateFileAndGetBytes(file);
        String publicId  = sanitizeUserId(publicUserId);
        return uploadToCloudinary(fileBytes, category, publicId, true);
    }

    /**
     * Upload image with userId and return secure URL (same as uploadImage).
     */
    public String uploadImageAndGetUrl(MultipartFile file, String category, String publicUserId) throws IOException {
        return uploadImage(file, category, publicUserId);
    }

    /**
     * Delete image from Cloudinary by its URL.
     * Accepts both Cloudinary URLs and legacy server URLs (legacy URLs are silently skipped).
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        String publicId = extractPublicId(imageUrl);
        if (publicId == null) {
            log.debug("Skipping delete — not a Cloudinary URL: {}", imageUrl);
            return;
        }

        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
            log.info("Deleted image from Cloudinary: {}", publicId);
        } catch (Exception e) {
            log.error("Failed to delete image from Cloudinary: {}", publicId, e);
        }
    }

    // ============================================================
    // PRIVATE — CLOUDINARY OPERATIONS
    // ============================================================

    @SuppressWarnings("unchecked")
    private String uploadToCloudinary(byte[] fileBytes, String folder, String publicId, boolean overwrite) throws IOException {
        try {
            Map<String, Object> params = ObjectUtils.asMap(
                    "folder",    "Afrochow/" + folder,
                    "public_id", publicId,
                    "overwrite", overwrite,
                    "resource_type", "image"
            );

            Map<String, Object> result = cloudinary.uploader().upload(fileBytes, params);
            String url = (String) result.get("secure_url");

            if (url == null || url.isBlank()) {
                throw new IOException("Cloudinary upload returned no URL");
            }

            log.info("Image uploaded to Cloudinary: {}", url);
            return url;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Cloudinary upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the Cloudinary public_id from a Cloudinary secure URL.
     * Format: https://res.cloudinary.com/{cloud}/image/upload/{version?}/{folder}/{publicId}.{ext}
     * Returns null if the URL is not a Cloudinary URL.
     */
    private String extractPublicId(String url) {
        final String marker = "/image/upload/";
        int idx = url.indexOf(marker);
        if (idx == -1) {
            return null; // not a Cloudinary URL
        }

        String rest = url.substring(idx + marker.length());

        // Strip optional version prefix like "v1234567890/"
        rest = rest.replaceFirst("^v\\d+/", "");

        // Strip file extension
        int lastDot = rest.lastIndexOf('.');
        if (lastDot != -1) {
            rest = rest.substring(0, lastDot);
        }

        return rest.isBlank() ? null : rest;
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private byte[] validateFileAndGetBytes(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ImageValidationException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new ImageValidationException("File size exceeds limit of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME.contains(contentType.toLowerCase())) {
            throw new ImageValidationException("Invalid file type. Allowed types: JPEG, PNG, GIF, WebP");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ImageValidationException("Invalid filename");
        }

        String extension = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ImageValidationException("Invalid file extension");
        }

        byte[] fileBytes = file.getBytes();

        validateMagicBytes(fileBytes);
        validateDimensions(fileBytes);

        return fileBytes;
    }

    private void validateMagicBytes(byte[] fileBytes) {
        if (fileBytes.length < 12) {
            throw new ImageValidationException("Invalid image file");
        }

        boolean validFormat = isJPEG(fileBytes) || isPNG(fileBytes) ||
                isGIF(fileBytes) || isWebP(fileBytes);

        if (!validFormat) {
            throw new ImageValidationException("Invalid image format");
        }
    }

    private void validateDimensions(byte[] fileBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new ImageValidationException("Unsupported image format");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);

                int width  = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;

                if (width > MAX_WIDTH || height > MAX_HEIGHT) {
                    throw new ImageValidationException(
                            String.format("Image dimensions too large. Max: %dx%d", MAX_WIDTH, MAX_HEIGHT));
                }

                if (pixels > MAX_PIXELS) {
                    throw new ImageValidationException("Image has too many pixels");
                }

            } finally {
                reader.dispose();
            }
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new ImageValidationException("Category is required");
        }
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new ImageValidationException("Invalid category: " + category);
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ImageValidationException("User ID is required");
        }
        if (userId.length() > MAX_USERID_LENGTH) {
            throw new ImageValidationException("User ID too long");
        }
        if (!userId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new ImageValidationException("User ID contains invalid characters");
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private boolean isJPEG(byte[] h) {
        return h[0] == (byte) 0xFF && h[1] == (byte) 0xD8;
    }

    private boolean isPNG(byte[] h) {
        return h[0] == (byte) 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47;
    }

    private boolean isGIF(byte[] h) {
        return h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46;
    }

    private boolean isWebP(byte[] h) {
        return h.length >= 12 && h[0] == 0x52 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x46 &&
                h[8] == 0x57 && h[9] == 0x45 && h[10] == 0x42 && h[11] == 0x50;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot).toLowerCase();
    }

    private String sanitizeUserId(String userId) {
        return userId.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
