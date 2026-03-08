package com.afrochow;

import com.afrochow.email.EmailService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EmailVerificationTest {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationTest.class);

    @Autowired
    private EmailService emailService;

    @Test
    public void testVerificationEmail() {
        try {
            String toEmail = "ogunbanwoayotunde@gmail.com";
            String verificationCode = "123456";
            String firstName = "Bukola";

            // ✅ Correct order: toEmail, verificationCode, firstName
            emailService.sendEmailVerificationEmail(toEmail, verificationCode, firstName);

            logger.info("✅ Verification email sent successfully!");
            logger.info("📧 Check your email for verification code: {}", verificationCode);
        } catch (Exception e) {
            logger.error("❌ Verification email failed: {}", e.getMessage(), e);
        }
    }
}