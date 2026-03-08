package com.afrochow.image;

import com.afrochow.common.exceptions.ImageNotFoundException;
import com.afrochow.common.exceptions.ImageValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
public class ImageUploadService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-file-size:5242880}")
    private long maxFileSize;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    // Whitelist of allowed categories
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "registrations", "licenses", "logos", "banners", "profiles",
            "vendors","VendorLogo","VendorBanner","VendorProfileImage" ,"products", "customers",
            "documents", "CustomerProfileImage", "admins",
            "AdminProfileImage","VendorBusinessLicense","vendors/banners","vendors/logos"
    );

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".gif", "image/gif",
            ".webp", "image/webp"
    );

    private static final int MAX_WIDTH = 10000;
    private static final int MAX_HEIGHT = 10000;
    private static final long MAX_PIXELS = 50_000_000L;
    private static final int MAX_USERID_LENGTH = 100;

    private final Object dirLock = new Object();

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Upload image for registration with UUID filename
     *
     * @param file     Image file to upload
     * @param category Image category
     * @return Relative path (category/filename)
     * @throws ImageValidationException if validation fails
     * @throws IOException              if upload fails
     */
    public String uploadImageForRegistration(MultipartFile file, String category) throws IOException {
        validateCategory(category);
        byte[] fileBytes = validateFileAndGetBytes(file);

        Path uploadPath = prepareUploadPath(category);
        String extension = getValidatedExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString() + extension;

        saveFile(uploadPath, filename, fileBytes);
        log.info("Registration image saved: {}/{}", category, filename);

        return category + "/" + filename;
    }

    /**
     * Upload image for registration and return full URL
     */
    public String uploadImageForRegistrationAndGetUrl(MultipartFile file, String category) throws IOException {
        String relativePath = uploadImageForRegistration(file, category);
        return getImageUrl(relativePath);
    }

    /**
     * Upload image using userId as filename (overwrites existing)
     *
     * @param file         Image file to upload
     * @param category     Image category
     * @param publicUserId User's public ID (becomes filename)
     * @return Relative path (category/filename)
     * @throws ImageValidationException if validation fails
     * @throws IOException              if upload fails
     */
    public String uploadImage(MultipartFile file, String category, String publicUserId) throws IOException {
        validateCategory(category);
        validateUserId(publicUserId);
        byte[] fileBytes = validateFileAndGetBytes(file);

        Path uploadPath = prepareUploadPath(category);
        String extension = getValidatedExtension(file.getOriginalFilename());
        String filename = sanitizeUserId(publicUserId) + extension;

        // Delete old image with different extension if exists
        deleteOldUserImages(uploadPath, publicUserId, extension);

        saveFile(uploadPath, filename, fileBytes);
        log.info("User image saved: {}/{}", category, filename);

        return category + "/" + filename;
    }

    /**
     * Upload image with userId and return full URL
     */
    public String uploadImageAndGetUrl(MultipartFile file, String category, String publicUserId) throws IOException {
        String relativePath = uploadImage(file, category, publicUserId);
        return getImageUrl(relativePath);
    }

    /**
     * Delete image by relative path
     *
     * @param relativePath Relative path (e.g., "vendors/abc123.jpg")
     */
    public void deleteImage(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        try {
            Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetPath = uploadDirPath.resolve(relativePath).normalize();

            if (!targetPath.startsWith(uploadDirPath)) {
                log.error("Path traversal attempt detected: {}", relativePath);
                throw new SecurityException("Invalid path");
            }

            boolean deleted = Files.deleteIfExists(targetPath);
            if (deleted) {
                log.info("Deleted image: {}", relativePath);
            } else {
                log.debug("Image not found for deletion: {}", relativePath);
            }

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete image: {}", relativePath, e);
        }
    }

    /**
     * Get image bytes by relative path
     *
     * @param relativePath Relative path (e.g., "vendors/abc123.jpg")
     * @return Image bytes
     * @throws ImageNotFoundException if image not found
     * @throws IOException            if read fails
     */
    public byte[] getImageBytes(String relativePath) throws IOException {
        Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetPath = uploadDirPath.resolve(relativePath).normalize();

        if (!targetPath.startsWith(uploadDirPath)) {
            log.error("Path traversal attempt detected: {}", relativePath);
            throw new SecurityException("Invalid path");
        }

        if (!Files.exists(targetPath)) {
            throw new ImageNotFoundException("Image not found: " + relativePath);
        }

        if (Files.isSymbolicLink(targetPath)) {
            log.error("Symbolic link detected: {}", relativePath);
            throw new SecurityException("Symbolic links not allowed");
        }

        return Files.readAllBytes(targetPath);
    }

    /**
     * Get content type for image
     *
     * @param relativePath Relative path
     * @return Content type (e.g., "image/jpeg")
     * @throws ImageNotFoundException if file not found
     * @throws IOException            if read fails
     */
    public String getContentType(String relativePath) throws IOException {
        Path targetPath = Paths.get(uploadDir).resolve(relativePath).normalize();

        if (!Files.exists(targetPath)) {
            throw new ImageNotFoundException("Image not found: " + relativePath);
        }

        // Try to probe content type
        String contentType = Files.probeContentType(targetPath);

        // Fallback to extension-based detection
        if (contentType == null) {
            String extension = getExtension(relativePath).toLowerCase();
            contentType = EXTENSION_TO_MIME.getOrDefault(extension, "application/octet-stream");
        }

        return contentType;
    }

    /**
     * Get full URL for relative path
     *
     * @param relativePath Relative path
     * @return Full URL or null if path is empty
     */
    public String getImageUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return appUrl + "/api/images/" + relativePath;
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

        // Read file bytes once
        byte[] fileBytes = file.getBytes();

        // Validate magic bytes
        validateMagicBytes(fileBytes);

        // Validate dimensions
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

                int width = reader.getWidth(0);
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
    // FILE OPERATIONS
    // ============================================================

    private Path prepareUploadPath(String category) throws IOException {
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path categoryPath = basePath.resolve(category).normalize();

        if (!categoryPath.startsWith(basePath)) {
            throw new SecurityException("Invalid upload path");
        }

        synchronized (dirLock) {
            if (!Files.exists(categoryPath)) {
                Files.createDirectories(categoryPath);
                log.info("Created directory: {}", category);
            }
        }

        return categoryPath;
    }

    private void saveFile(Path uploadPath, String filename, byte[] fileBytes) throws IOException {
        Path targetPath = uploadPath.resolve(filename).normalize();

        if (!targetPath.startsWith(uploadPath)) {
            throw new SecurityException("Invalid file path");
        }

        // Check for symlinks before writing
        if (Files.exists(targetPath)) {
            if (Files.isSymbolicLink(targetPath)) {
                throw new SecurityException("Symbolic link detected at target path");
            }
        }

        // Write file
        Files.write(targetPath, fileBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Verify no symlink was created (Time-Of-Check-Time-Of-Use protection)
        if (Files.isSymbolicLink(targetPath)) {
            Files.deleteIfExists(targetPath);
            throw new SecurityException("Symbolic link detected after write");
        }
    }

    private void deleteOldUserImages(Path uploadPath, String userId, String currentExtension) {
        String sanitizedUserId = sanitizeUserId(userId);

        for (String ext : ALLOWED_EXTENSIONS) {
            if (!ext.equals(currentExtension)) {
                try {
                    Path oldFile = uploadPath.resolve(sanitizedUserId + ext);
                    Files.deleteIfExists(oldFile);
                } catch (Exception e) {
                    log.debug("Could not delete old image: {}", sanitizedUserId + ext);
                }
            }
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
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot).toLowerCase();
    }

    private String getValidatedExtension(String filename) {
        String extension = getExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ImageValidationException("Invalid file extension");
        }
        return extension;
    }

    private String sanitizeUserId(String userId) {
        return userId.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}