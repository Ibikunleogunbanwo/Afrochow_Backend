package com.afrochow.image;

import com.afrochow.common.exceptions.ImageValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ImageUploadService} running in "dev mode" (no Cloudinary bean
 * injected, so uploads fall back to local filesystem storage under a temp directory).
 *
 * Focused on the certificate upload path added to support PDF compliance documents
 * (food handling certificates) alongside the existing image-only upload path used for
 * logos, banners, and product photos.
 */
class ImageUploadServiceTest {

    private ImageUploadService imageUploadService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        imageUploadService = new ImageUploadService();
        ReflectionTestUtils.setField(imageUploadService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(imageUploadService, "appUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(imageUploadService, "maxFileSize", 10_485_760L); // 10MB
        // "cloudinary" field is left null (as in dev profile), so uploads go to local disk.
    }

    private static byte[] validPngBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] minimalPdfBytes() {
        return "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes();
    }

    @Test
    void uploadCertificate_acceptsPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", minimalPdfBytes());

        String url = imageUploadService.uploadCertificateForRegistrationAndGetUrl(file, "vendors/certifications");

        assertThat(url).contains("/api/images/vendors/certifications/");
        assertThat(url).endsWith(".pdf");
    }

    @Test
    void uploadCertificate_acceptsPng() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.png", "image/png", validPngBytes());

        String url = imageUploadService.uploadCertificateForRegistrationAndGetUrl(file, "vendors/certifications");

        assertThat(url).contains("/api/images/vendors/certifications/");
        assertThat(url).endsWith(".png");
    }

    @Test
    void uploadCertificate_rejectsFileWhoseBytesAreNeitherPdfNorImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "not a real pdf or image".getBytes());

        assertThatThrownBy(() ->
                imageUploadService.uploadCertificateForRegistrationAndGetUrl(file, "vendors/certifications"))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void uploadCertificate_rejectsVeryShortFileWithoutCrashing() {
        // Regression guard: a too-short byte array must raise a clean validation error,
        // not an ArrayIndexOutOfBoundsException from the format-sniffing helpers.
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() ->
                imageUploadService.uploadCertificateForRegistrationAndGetUrl(file, "vendors/certifications"))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void uploadCertificate_rejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.exe", "application/octet-stream", "malicious".getBytes());

        assertThatThrownBy(() ->
                imageUploadService.uploadCertificateForRegistrationAndGetUrl(file, "vendors/certifications"))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void uploadImage_stillRejectsPdfForNonCertificateCategories() {
        // Logos, banners, and product photos must remain image-only even though
        // certificate uploads now accept PDF.
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.pdf", "application/pdf", minimalPdfBytes());

        assertThatThrownBy(() ->
                imageUploadService.uploadImageForRegistrationAndGetUrl(file, "vendors/logos"))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void uploadImage_stillAcceptsPngForNonCertificateCategories() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", validPngBytes());

        String url = imageUploadService.uploadImageForRegistrationAndGetUrl(file, "vendors/logos");

        assertThat(url).contains("/api/images/vendors/logos/");
    }
}
