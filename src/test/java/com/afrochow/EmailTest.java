package com.afrochow;

import com.afrochow.email.EmailService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EmailTest {

    private static final Logger logger = LoggerFactory.getLogger(EmailTest.class);

    @Autowired
    private EmailService emailService;

    @Test
    public void testSendEmail() {
        try {
            emailService.sendWelcomeEmail("ogunbanwoibikunlea@gmail.com", "Test User","Customer");
            logger.info("Email sent successfully!");
        } catch (Exception e) {
            logger.error("Email failed: {}", e.getMessage(), e);
        }
    }
}
