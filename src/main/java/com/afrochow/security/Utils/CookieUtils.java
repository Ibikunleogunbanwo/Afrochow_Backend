package com.afrochow.security.Utils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

public final class CookieUtils {

    private CookieUtils() {}

    /**
     * Create and add an HttpOnly cookie to the response.
     *
     * @param response   HttpServletResponse to add the cookie
     * @param name       Cookie name
     * @param value      Cookie value
     * @param maxAgeSec  TTL in seconds
     * @param secure     Secure flag (HTTPS only)
     * @param sameSite   SameSite attribute ("Lax", "Strict", or "None")
     * @param domain     Cookie domain (e.g. ".afrochow.ca"); blank or null = let browser default to request host
     */
    public static void addHttpOnlyCookie(
            HttpServletResponse response,
            String name,
            String value,
            long maxAgeSec,
            boolean secure,
            String sameSite,
            String domain
    ) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSec))
                .sameSite(sameSite);

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
