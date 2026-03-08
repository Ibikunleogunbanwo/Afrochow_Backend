package com.afrochow.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility to generate BCrypt password hashes for database scripts
 *
 * Usage: Run this class to generate password hashes
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12); // 12 rounds (matches SecurityConfig)

        String password = "REDACTED";
        String hash = encoder.encode(password);

        System.out.println("======================================");
        System.out.println("BCrypt Password Hash Generator");
        System.out.println("======================================");
        System.out.println("Plain Password: " + password);
        System.out.println("BCrypt Hash (12 rounds): ");
        System.out.println(hash);
        System.out.println("======================================");
        System.out.println();
        System.out.println("Copy this hash into your SQL script:");
        System.out.println("'" + hash + "'");
        System.out.println("======================================");

        // Generate a few more common passwords
        System.out.println("\nAdditional hashes for testing:");
        System.out.println("--------------------------------------");

        String[] testPasswords = {
            "Test@123",
            "Customer@123",
            "Vendor@123",
            "Support@123"
        };

        for (String pwd : testPasswords) {
            String testHash = encoder.encode(pwd);
            System.out.println("Password: " + pwd);
            System.out.println("Hash: " + testHash);
            System.out.println();
        }
    }
}
