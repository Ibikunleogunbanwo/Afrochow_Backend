package com.afrochow.image.service;

import com.afrochow.common.exceptions.ImageNotFoundException;
import com.afrochow.common.exceptions.ImageValidationException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Injected only in prod profile — null in dev
    @Autowired(required = false)
    private Cloudinary cloudinary;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    @Value("${app.upload.max-file-size:5242880}")
    private long maxFileSize;

    private static final String CLOUDINARY_ROOT_FOLDER = "Afrochow";

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "registrations", "licenses", "logos", "banners", "profiles",
            "vendors", "VendorLogo", "VendorBanner", "VendorProfileImage", "products", "customers",
            "documents", "CustomerProfileImage", "admins",
            "AdminProfileImage", "VendorBusinessLicense", "vendors/banners", "vendors/logos",
            "vendors/certifications", "customer/profile_image"
    );

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    // Certificate/document uploads (e.g. vendor food handling certificates) accept
    // everything a regular image upload does, plus PDF, since compliance documents
    // are commonly scanned or exported as PDF rather than a photo.
    private static final Set<String> CERT_ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "application/pdf"
    );

    private static final Set<String> CERT_ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf"
    );

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            ".jpg",  "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png",  "image/png",
            ".gif",  "image/gif",
            ".webp", "image/webp",
            ".pdf",  "application/pdf"
    );

    private static final int  MAX_WIDTH        = 10000;
    private static final int  MAX_HEIGHT       = 10000;
    private static final long MAX_PIXELS       = 50_000_000L;

    private final Object dirLock = new Object();

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Upload image with UUID filename.
     * Prod → Cloudinary.  Dev → local filesystem.
     */
    public String uploadImageForRegistration(MultipartFile file, String category) throws IOException {
        validateCategory(category);
        byte[] fileBytes = validateFileAndGetBytes(file);
        String uuid      = UUID.randomUUID().toString();

        if (cloudinary != null) {
            return uploadToCloudinary(fileBytes, category, uuid, false);
        }

        String extension = getValidatedExtension(file.getOriginalFilename());
        Path   uploadPath = prepareUploadPath(category);
        String filename   = uuid + extension;
        saveFile(uploadPath, filename, fileBytes);
        log.info("Local image saved: {}/{}", category, filename);
        return getImageUrl(category + "/" + filename);
    }

    public String uploadImageForRegistrationAndGetUrl(MultipartFile file, String category) throws IOException {
        return uploadImageForRegistration(file, category);
    }

    /**
     * Upload a compliance document (e.g. a vendor food handling certificate) with UUID filename.
     * Accepts JPEG/PNG/GIF/WebP like a regular image upload, plus PDF, since certificates are
     * commonly scanned or exported as PDF. Prod → Cloudinary. Dev → local filesystem.
     */
    public String uploadCertificateForRegistrationAndGetUrl(MultipartFile file, String category) throws IOException {
        validateCategory(category);
        byte[] fileBytes = validateCertificateFileAndGetBytes(file);
        String uuid      = UUID.randomUUID().toString();

        if (cloudinary != null) {
            return uploadToCloudinary(fileBytes, category, uuid, false);
        }

        String extension = getValidatedExtension(file.getOriginalFilename(), CERT_ALLOWED_EXTENSIONS);
        Path   uploadPath = prepareUploadPath(category);
        String filename   = uuid + extension;
        saveFile(uploadPath, filename, fileBytes);
        log.info("Local certificate saved: {}/{}", category, filename);
        return getImageUrl(category + "/" + filename);
    }

    /**
     * Delete image.
     * Prod → delete from Cloudinary.  Dev → delete from local filesystem.
     */
    public void deleteImage(String imageUrl) {
        deleteImageBestEffort(imageUrl);
    }

    public boolean deleteImageBestEffort(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return true;

        try {
            deleteImageOrThrow(imageUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete image: {}", imageUrl, e);
            return false;
        }
    }

    public void deleteImageOrThrow(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isBlank()) return;

        if (cloudinary != null) {
            deleteFromCloudinaryOrThrow(imageUrl);
        } else {
            deleteFromLocalOrThrow(imageUrl);
        }
    }

    /**
     * Read image bytes from local filesystem (dev only).
     */
    public byte[] getImageBytes(String relativePath) throws IOException {
        Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetPath    = uploadDirPath.resolve(relativePath).normalize();

        if (!targetPath.startsWith(uploadDirPath)) {
            throw new SecurityException("Invalid path");
        }
        if (!Files.exists(targetPath)) {
            throw new ImageNotFoundException("Image not found: " + relativePath);
        }
        if (Files.isSymbolicLink(targetPath)) {
            throw new SecurityException("Symbolic links not allowed");
        }

        return Files.readAllBytes(targetPath);
    }

    /**
     * Get content type for a local image file (dev only).
     */
    public String getContentType(String relativePath) throws IOException {
        Path targetPath = Paths.get(uploadDir).resolve(relativePath).normalize();

        if (!Files.exists(targetPath)) {
            throw new ImageNotFoundException("Image not found: " + relativePath);
        }

        String contentType = Files.probeContentType(targetPath);
        if (contentType == null) {
            String ext = getExtension(relativePath).toLowerCase();
            contentType = EXTENSION_TO_MIME.getOrDefault(ext, "application/octet-stream");
        }
        return contentType;
    }

    /**
     * Build a full local URL from a relative path (dev only).
     */
    public String getImageUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        return appUrl + "/api/images/" + relativePath;
    }

    // ============================================================
    // CLOUDINARY OPERATIONS
    // ============================================================

    @SuppressWarnings("unchecked")
    private String uploadToCloudinary(byte[] fileBytes, String folder, String publicId, boolean overwrite) throws IOException {
        try {
            Map<String, Object> params = ObjectUtils.asMap(
                    "folder",        CLOUDINARY_ROOT_FOLDER + "/" + folder,
                    "public_id",     publicId,
                    "overwrite",     overwrite,
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

    private void deleteFromCloudinaryOrThrow(String imageUrl) throws IOException {
        String publicId = extractCloudinaryPublicId(imageUrl);
        if (publicId == null) {
            log.debug("Skipping Cloudinary delete — not a Cloudinary URL: {}", imageUrl);
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
            log.info("Deleted from Cloudinary: {}", publicId);
        } catch (Exception e) {
            throw new IOException("Cloudinary delete failed for " + publicId, e);
        }
    }

    /**
     * Extracts the public_id from a Cloudinary URL.
     * e.g. https://res.cloudinary.com/cloud/image/upload/v123/Afrochow/products/uuid.jpg
     *   →  Afrochow/products/uuid
     */
    private String extractCloudinaryPublicId(String url) {
        final String marker = "/image/upload/";
        int idx = url.indexOf(marker);
        if (idx == -1) return null;

        String rest = url.substring(idx + marker.length());
        rest = rest.replaceFirst("^v\\d+/", "");   // strip version
        int lastDot = rest.lastIndexOf('.');
        if (lastDot != -1) rest = rest.substring(0, lastDot); // strip extension
        return rest.isBlank() ? null : rest;
    }

    // ============================================================
    // LOCAL FILESYSTEM OPERATIONS
    // ============================================================

    private void deleteFromLocalOrThrow(String imageUrl) throws IOException {
        String prefix1 = appUrl + "/api/images/";
        String prefix2 = appUrl + "/images/";

        String relativePath;
        if (imageUrl.startsWith(prefix1)) {
            relativePath = imageUrl.substring(prefix1.length());
        } else if (imageUrl.startsWith(prefix2)) {
            relativePath = imageUrl.substring(prefix2.length());
        } else {
            log.debug("Skipping local delete — URL does not match server: {}", imageUrl);
            return;
        }

        Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetPath    = uploadDirPath.resolve(relativePath).normalize();

        if (!targetPath.startsWith(uploadDirPath)) {
            log.error("Path traversal attempt detected: {}", relativePath);
            throw new SecurityException("Invalid path");
        }

        boolean deleted = Files.deleteIfExists(targetPath);
        if (deleted) log.info("Deleted local image: {}", relativePath);
    }

    private Path prepareUploadPath(String category) throws IOException {
        Path basePath     = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path categoryPath = basePath.resolve(category).normalize();

        if (!categoryPath.startsWith(basePath)) {
            throw new SecurityException("Invalid upload path");
        }

        synchronized (dirLock) {
            if (!Files.exists(categoryPath)) {
                Files.createDirectories(categoryPath);
            }
        }
        return categoryPath;
    }

    private void saveFile(Path uploadPath, String filename, byte[] fileBytes) throws IOException {
        Path targetPath = uploadPath.resolve(filename).normalize();

        if (!targetPath.startsWith(uploadPath)) {
            throw new SecurityException("Invalid file path");
        }
        if (Files.exists(targetPath) && Files.isSymbolicLink(targetPath)) {
            throw new SecurityException("Symbolic link detected at target path");
        }

        Files.write(targetPath, fileBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        if (Files.isSymbolicLink(targetPath)) {
            Files.deleteIfExists(targetPath);
            throw new SecurityException("Symbolic link detected after write");
        }
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

    private void validateMagicBytes(byte[] h) {
        if (h.length < 12) throw new ImageValidationException("Invalid image file");
        if (!isJPEG(h) && !isPNG(h) && !isGIF(h) && !isWebP(h)) {
            throw new ImageValidationException("Invalid image format");
        }
    }

    /**
     * Same checks as {@link #validateFileAndGetBytes}, but also accepts PDF for
     * compliance-document uploads (food handling certificates). The actual file bytes,
     * not just the declared content type or extension, decide whether a file is treated
     * as a PDF or an image: dimension validation only applies to genuine raster images.
     */
    private byte[] validateCertificateFileAndGetBytes(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ImageValidationException("File is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new ImageValidationException("File size exceeds limit of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !CERT_ALLOWED_MIME.contains(contentType.toLowerCase())) {
            throw new ImageValidationException("Invalid file type. Allowed types: JPEG, PNG, GIF, WebP, PDF");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ImageValidationException("Invalid filename");
        }

        String extension = getExtension(originalFilename).toLowerCase();
        if (!CERT_ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ImageValidationException("Invalid file extension");
        }

        byte[] fileBytes = file.getBytes();
        if (fileBytes.length < 5) {
            throw new ImageValidationException("Invalid file");
        }
        if (isPDF(fileBytes)) {
            // Valid PDF signature — no raster dimension check applies.
            return fileBytes;
        }
        // Not a PDF: fall back to the same minimum-length guard the image-only path uses
        // before touching any further signature bytes (isPNG/isWebP read up to index 11).
        if (fileBytes.length < 12) {
            throw new ImageValidationException("Invalid file");
        }
        if (isJPEG(fileBytes) || isPNG(fileBytes) || isGIF(fileBytes) || isWebP(fileBytes)) {
            validateDimensions(fileBytes);
            return fileBytes;
        }
        throw new ImageValidationException("Invalid file format. Allowed types: JPEG, PNG, GIF, WebP, PDF");
    }

    private void validateDimensions(byte[] fileBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new ImageValidationException("Unsupported image format");

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
                if (pixels > MAX_PIXELS) throw new ImageValidationException("Image has too many pixels");
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) throw new ImageValidationException("Category is required");
        if (!ALLOWED_CATEGORIES.contains(category)) throw new ImageValidationException("Invalid category: " + category);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private boolean isJPEG(byte[] h) { return h[0] == (byte)0xFF && h[1] == (byte)0xD8; }
    private boolean isPNG(byte[] h)  { return h[0] == (byte)0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47; }
    private boolean isGIF(byte[] h)  { return h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46; }
    private boolean isWebP(byte[] h) {
        return h.length >= 12 && h[0] == 0x52 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x46
                && h[8] == 0x57 && h[9] == 0x45 && h[10] == 0x42 && h[11] == 0x50;
    }

    // PDF signature: "%PDF-" (0x25 0x50 0x44 0x46 0x2D)
    private boolean isPDF(byte[] h) {
        return h.length >= 5 && h[0] == 0x25 && h[1] == 0x50 && h[2] == 0x44 && h[3] == 0x46 && h[4] == 0x2D;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot).toLowerCase();
    }

    private String getValidatedExtension(String filename) {
        return getValidatedExtension(filename, ALLOWED_EXTENSIONS);
    }

    private String getValidatedExtension(String filename, Set<String> allowedExtensions) {
        String ext = getExtension(filename);
        if (!allowedExtensions.contains(ext)) throw new ImageValidationException("Invalid file extension");
        return ext;
    }

}
