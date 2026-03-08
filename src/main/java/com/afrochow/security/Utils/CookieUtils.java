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
     */
    public static void addHttpOnlyCookie(
            HttpServletResponse response,
            String name,
            String value,
            long maxAgeSec,
            boolean secure,
            String sameSite
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSec))
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
