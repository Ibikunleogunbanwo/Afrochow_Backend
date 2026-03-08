package com.afrochow.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SMS Notification Service (Future Implementation)
 *
 * This service will handle sending SMS notifications for critical events.
 * Integration options:
 * - Twilio SMS API
 * - AWS SNS (Simple Notification Service)
 * - Africa's Talking (for African markets)
 *
 * SMS should be reserved for very critical notifications due to cost:
 * - Payment confirmations
 * - Order delivered
 * - Security alerts (password changes, login from new device)
 *
 * TODO: Implement when SMS provider is selected and funded
 */
@Slf4j
@Service
public class SmsService {

    /**
     * Send SMS to a phone number
     *
     * @param phoneNumber Recipient's phone number (E.164 format: +1234567890)
     * @param message SMS message content
     */
    public void sendSms(String phoneNumber, String message) {
        log.info("SMS service not yet implemented. Would send to {}: {}",
            phoneNumber, message);

        // TODO: Implement Twilio/AWS SNS integration
        // 1. Validate phone number format (E.164)
        // 2. Check SMS credits/balance
        // 3. Send via SMS provider API
        // 4. Log delivery status
        // 5. Handle failures and retries
    }

    /**
     * Send order confirmation SMS
     *
     * @param phoneNumber Customer's phone number
     * @param orderPublicId Order ID
     * @param vendorName Restaurant name
     */
    public void sendOrderConfirmationSms(String phoneNumber, String orderPublicId,
                                         String vendorName) {
        String message = String.format("Afrochow: Your order #%s from %s has been confirmed!",
            orderPublicId, vendorName);

        sendSms(phoneNumber, message);
    }

    /**
     * Send order delivered SMS
     *
     * @param phoneNumber Customer's phone number
     * @param orderPublicId Order ID
     */
    public void sendOrderDeliveredSms(String phoneNumber, String orderPublicId) {
        String message = String.format("Afrochow: Your order #%s has been delivered. Enjoy your meal!",
            orderPublicId);

        sendSms(phoneNumber, message);
    }

    /**
     * Send payment confirmation SMS
     *
     * @param phoneNumber Customer's phone number
     * @param amount Payment amount
     * @param orderPublicId Order ID
     */
    public void sendPaymentConfirmationSms(String phoneNumber, String amount,
                                           String orderPublicId) {
        String message = String.format("Afrochow: Payment of %s received for order #%s. Thank you!",
            amount, orderPublicId);

        sendSms(phoneNumber, message);
    }

    /**
     * Send security alert SMS
     *
     * @param phoneNumber User's phone number
     * @param alertType Type of security alert
     * @param details Alert details
     */
    public void sendSecurityAlertSms(String phoneNumber, String alertType, String details) {
        String message = String.format("Afrochow Security Alert: %s - %s",
            alertType, details);

        sendSms(phoneNumber, message);
    }

    /**
     * Send verification code SMS
     *
     * @param phoneNumber User's phone number
     * @param code Verification code
     */
    public void sendVerificationCodeSms(String phoneNumber, String code) {
        String message = String.format("Afrochow: Your verification code is %s. Valid for 10 minutes.",
            code);

        sendSms(phoneNumber, message);
    }

    /**
     * Validate phone number format (E.164)
     *
     * @param phoneNumber Phone number to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        // E.164 format: +[country code][number]
        // Example: +12345678901 (1-15 digits)
        String e164Regex = "^\\+[1-9]\\d{1,14}$";
        return phoneNumber != null && phoneNumber.matches(e164Regex);
    }
}
