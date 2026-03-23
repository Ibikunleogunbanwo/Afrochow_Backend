package com.afrochow.common.validation;

/**
 * Utility for Canadian phone number normalization and validation.
 *
 * Accepted input formats:
 *   4031234567
 *   403-123-4567
 *   (403) 123-4567
 *   403.123.4567
 *   +1 403 123 4567
 *   14031234567
 *
 * Stored format: 10 plain digits — e.g. 4031234567
 *
 * Canadian rules:
 *   - Exactly 10 digits after stripping formatting and optional country code
 *   - Area code (first 3): cannot start with 0 or 1
 *   - Exchange code (digits 4-6): cannot start with 0 or 1
 */
public final class PhoneUtils {

    private PhoneUtils() {}

    /**
     * Strip all non-digit characters, remove leading country code +1 or 1,
     * and return a 10-digit string. Returns null if the result is not 10 digits.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;

        String digits = raw.replaceAll("[^0-9]", "");

        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }

        return digits.length() == 10 ? digits : null;
    }

    /**
     * Returns true if the normalized (10-digit) string is a valid Canadian number.
     * Area code and exchange code must not start with 0 or 1.
     */
    public static boolean isValid(String normalized) {
        if (normalized == null || normalized.length() != 10) return false;
        char areaFirst = normalized.charAt(0);
        char exchangeFirst = normalized.charAt(3);
        return areaFirst != '0' && areaFirst != '1'
                && exchangeFirst != '0' && exchangeFirst != '1';
    }
}
