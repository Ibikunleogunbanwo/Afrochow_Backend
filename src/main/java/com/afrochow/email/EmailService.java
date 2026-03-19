package com.afrochow.email;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for sending email notifications via Resend HTTP API.
 *
 * SECURITY:
 * - Uses Resend HTTP API (HTTPS/443) — not SMTP — to work on Railway
 * - Email addresses validated before sending
 * - API key stored in environment variables
 * - Rate limiting applied per endpoint
 *
 * FEATURES:
 * - HTML emails with Thymeleaf templates
 * - Email verification, password reset, welcome emails
 * - Order confirmation and status update emails
 * - Vendor new order notifications
 * - Payment confirmation and failure emails
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final Resend resend;
    private final TemplateEngine templateEngine;

    @Value("${resend.from:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${spring.mail.from-name:Afrochow}")
    private String fromName;

    @Value("${app.name:Afrochow}")
    private String appName;

    @Value("${app.url:https://afrochow.com}")
    private String appUrl;

    @Getter
    @Value("${spring.mail.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.email.verification.expiration-minutes:1440}")
    private int verificationExpirationMinutes;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    public EmailService(Resend resend, TemplateEngine templateEngine) {
        this.resend = resend;
        this.templateEngine = templateEngine;
    }

    // ========== AUTHENTICATION EMAILS ==========

    /**
     * Send welcome email to new users
     */
    public void sendWelcomeEmail(String toEmail, String firstName, String role) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send welcome email to: {}", toEmail);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("role", role);
            context.setVariable("appName", appName);
            context.setVariable("appUrl", appUrl);

            String subject = String.format("Welcome to %s!", appName);
            String htmlContent = processTemplate("welcome", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Welcome email sent to: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to send welcome email to: {}", toEmail, e);
        }
    }

    /**
     * Send password reset email with token
     *
     * SECURITY:
     * - Token is single-use
     * - Token expires after configured time
     * - Link includes token in URL parameter
     */
    public void sendPasswordResetEmail(String email, String firstName, String resetLink) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send password reset email to: {}", email);
            return;
        }

        try {
            validateEmail(email);

            Context context = new Context();
            context.setVariable("appName", appName);
            context.setVariable("appUrl", appUrl);
            context.setVariable("firstName", firstName);
            context.setVariable("resetLink", resetLink);

            String subject = String.format("Password Reset Request - %s", appName);
            String htmlContent = processTemplate("password-reset", context);

            sendHtmlEmail(email, subject, htmlContent);
            logger.info("Password reset email sent to: {} with link: {}", email, resetLink);

        } catch (Exception e) {
            logger.error("Failed to send password reset email", e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    /**
     * Send password changed confirmation email
     *
     * SECURITY:
     * - Notifies user of successful password change
     * - Helps detect unauthorized password changes
     */
    public void sendPasswordChangedEmail(String toEmail, String firstName) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send password changed email to: {}", toEmail);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("appName", appName);
            context.setVariable("changeTime", LocalDateTime.now().format(DATE_FORMATTER));

            String subject = String.format("Password Changed - %s", appName);
            String htmlContent = processTemplate("password-changed", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Password changed email sent to: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to send password changed email to: {}", toEmail, e);
        }
    }

    /**
     * Send email verification email with code
     *
     * SECURITY:
     * - Code is single-use
     * - Code expires after configured time
     * - 6-digit numeric code for easy entry
     *
     * @param toEmail            User's email address
     * @param verificationCode   6-digit verification code
     * @param firstName          User's first name for personalization
     */
    public void sendEmailVerificationEmail(String toEmail, String verificationCode, String firstName) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send email verification email to: {}", toEmail);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("verificationCode", verificationCode);
            context.setVariable("expirationMinutes", verificationExpirationMinutes);
            context.setVariable("appName", appName);

            String subject = String.format("Verify Your Email - %s", appName);

            logger.info("Processing email template: email-verification");
            String htmlContent = processTemplate("email-verification", context);
            logger.info("Template processed successfully. Content length: {}", htmlContent.length());

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Email verification email sent to: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to send email verification email to: {} — {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send verification email. Please try again or contact support.", e);
        }
    }

    // ========== ORDER EMAILS ==========

    /**
     * Send order confirmation email to customer
     */
    public void sendOrderConfirmationEmail(
            String toEmail,
            String customerName,
            String orderPublicId,
            String vendorName,
            BigDecimal totalAmount,
            LocalDateTime orderTime) {

        if (!emailEnabled) {
            logger.info("Email disabled. Would send order confirmation email to: {} for order: {}",
                    toEmail, orderPublicId);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("orderPublicId", orderPublicId);
            context.setVariable("vendorName", vendorName);
            context.setVariable("totalAmount", String.format("$%.2f", totalAmount));
            context.setVariable("orderTime", orderTime.format(DATE_FORMATTER));
            context.setVariable("appName", appName);
            context.setVariable("orderTrackingUrl", String.format("%s/orders/%s", appUrl, orderPublicId));

            String subject = String.format("Order Confirmation #%s - %s", orderPublicId, appName);
            String htmlContent = processTemplate("order-confirmation", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Order confirmation email sent to: {} for order: {}", toEmail, orderPublicId);

        } catch (Exception e) {
            logger.error("Failed to send order confirmation email to: {} for order: {}",
                    toEmail, orderPublicId, e);
        }
    }

    /**
     * Send order status update email
     */
    public void sendOrderStatusUpdateEmail(
            String toEmail,
            String customerName,
            String orderPublicId,
            String oldStatus,
            String newStatus) {

        if (!emailEnabled) {
            logger.info("Email disabled. Would send order status update email to: {} for order: {}",
                    toEmail, orderPublicId);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("orderPublicId", orderPublicId);
            context.setVariable("oldStatus", oldStatus);
            context.setVariable("newStatus", newStatus);
            context.setVariable("appName", appName);
            context.setVariable("orderTrackingUrl", String.format("%s/orders/%s", appUrl, orderPublicId));

            String subject = String.format("Order Update: %s - %s", newStatus, appName);
            String htmlContent = processTemplate("order-status-update", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Order status update email sent to: {} for order: {}", toEmail, orderPublicId);

        } catch (Exception e) {
            logger.error("Failed to send order status update email to: {} for order: {}",
                    toEmail, orderPublicId, e);
        }
    }

    /**
     * Send new order notification to vendor
     */
    public void sendNewOrderNotificationToVendor(
            String toEmail,
            String vendorName,
            String orderPublicId,
            String customerName,
            BigDecimal totalAmount) {

        if (!emailEnabled) {
            logger.info("Email disabled. Would send new order notification to vendor: {} for order: {}",
                    toEmail, orderPublicId);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("vendorName", vendorName);
            context.setVariable("orderPublicId", orderPublicId);
            context.setVariable("customerName", customerName);
            context.setVariable("totalAmount", String.format("$%.2f", totalAmount));
            context.setVariable("appName", appName);
            context.setVariable("orderManagementUrl", String.format("%s/vendor/orders/%s", appUrl, orderPublicId));

            String subject = String.format("New Order #%s - %s", orderPublicId, appName);
            String htmlContent = processTemplate("vendor-new-order", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("New order notification sent to vendor: {} for order: {}", toEmail, orderPublicId);

        } catch (Exception e) {
            logger.error("Failed to send new order notification to vendor: {} for order: {}",
                    toEmail, orderPublicId, e);
        }
    }

    // ========== PAYMENT EMAILS ==========

    /**
     * Send payment confirmation email
     */
    public void sendPaymentConfirmationEmail(
            String toEmail,
            String customerName,
            String paymentPublicId,
            String orderPublicId,
            BigDecimal amount) {

        if (!emailEnabled) {
            logger.info("Email disabled. Would send payment confirmation email to: {} for payment: {}",
                    toEmail, paymentPublicId);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("paymentPublicId", paymentPublicId);
            context.setVariable("orderPublicId", orderPublicId);
            context.setVariable("amount", String.format("$%.2f", amount));
            context.setVariable("paymentTime", LocalDateTime.now().format(DATE_FORMATTER));
            context.setVariable("appName", appName);

            String subject = String.format("Payment Confirmation - %s", appName);
            String htmlContent = processTemplate("payment-confirmation", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Payment confirmation email sent to: {} for payment: {}", toEmail, paymentPublicId);

        } catch (Exception e) {
            logger.error("Failed to send payment confirmation email to: {} for payment: {}",
                    toEmail, paymentPublicId, e);
        }
    }

    /**
     * Send payment failed email
     */
    public void sendPaymentFailedEmail(
            String toEmail,
            String customerName,
            String orderPublicId,
            String reason) {

        if (!emailEnabled) {
            logger.info("Email disabled. Would send payment failed email to: {} for order: {}",
                    toEmail, orderPublicId);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("orderPublicId", orderPublicId);
            context.setVariable("reason", reason);
            context.setVariable("appName", appName);
            context.setVariable("retryUrl", String.format("%s/orders/%s/payment", appUrl, orderPublicId));

            String subject = String.format("Payment Failed - %s", appName);
            String htmlContent = processTemplate("payment-failed", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Payment failed email sent to: {} for order: {}", toEmail, orderPublicId);

        } catch (Exception e) {
            logger.error("Failed to send payment failed email to: {} for order: {}",
                    toEmail, orderPublicId, e);
        }
    }

    // ========== NOTIFICATION EMAILS ==========

    /**
     * Send generic notification email
     */
    public void sendNotificationEmail(String toEmail, String userName, String title, String message) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send notification email to: {}", toEmail);
            return;
        }

        try {
            validateEmail(toEmail);

            Context context = new Context();
            context.setVariable("userName", userName);
            context.setVariable("title", title);
            context.setVariable("message", message);
            context.setVariable("appName", appName);

            String subject = String.format("%s - %s", title, appName);
            String htmlContent = processTemplate("notification", context);

            sendHtmlEmail(toEmail, subject, htmlContent);
            logger.info("Notification email sent to: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to send notification email to: {}", toEmail, e);
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Validate email address format
     */
    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be null or empty");
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(emailRegex)) {
            throw new IllegalArgumentException("Invalid email address format: " + email);
        }
    }

    /**
     * Send HTML email via Resend HTTP API (port 443 — works on Railway)
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws Exception {
        String fromField = String.format("%s <%s>", fromName, fromEmail);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromField)
                .to(List.of(to))
                .subject(subject)
                .html(htmlContent)
                .build();

        resend.emails().send(params);
    }

    /**
     * Send plain text email via Resend HTTP API
     */
    public void sendPlainTextEmail(String to, String subject, String text) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send plain text email to: {}", to);
            return;
        }

        try {
            validateEmail(to);
            String fromField = String.format("%s <%s>", fromName, fromEmail);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromField)
                    .to(List.of(to))
                    .subject(subject)
                    .text(text)
                    .build();

            resend.emails().send(params);
            logger.info("Plain text email sent to: {}", to);

        } catch (Exception e) {
            logger.error("Failed to send plain text email to: {}", to, e);
        }
    }

    /**
     * Process Thymeleaf template with context
     */
    private String processTemplate(String templateName, Context context) {
        return templateEngine.process("email/" + templateName, context);
    }
}
