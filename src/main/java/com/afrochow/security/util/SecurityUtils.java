package com.afrochow.security.util;

import com.afrochow.common.exceptions.InsufficientPermissionException;
import com.afrochow.common.exceptions.UnauthorizedException;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.Role;
import com.afrochow.security.model.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for accessing Spring Security context information.
 * Works with CustomUserDetails to extract user information safely.
 *
 * <p>All methods are static and return safe defaults (null/false) when
 * no authentication context exists.
 */
@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {}

    /* ==========================================================
       USER IDENTITY METHODS
       ========================================================== */

    /**
     * Get the currently authenticated CustomUserDetails.
     *
     * @return CustomUserDetails or null if not authenticated or not CustomUserDetails
     */
    public static CustomUserDetails getCurrentUserDetails() {
        Authentication authentication = getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails) {
            return (CustomUserDetails) principal;
        }

        log.debug("Principal is not CustomUserDetails: {}",
                principal != null ? principal.getClass().getName() : "null");
        return null;
    }

    /**
     * Get the username of the currently authenticated user.
     *
     * @return username as String, or null if not authenticated
     */
    public static String getCurrentUsername() {
        CustomUserDetails userDetails = getCurrentUserDetails();
        return userDetails != null ? userDetails.getUsername() : null;
    }

    /**
     * Get the email of the currently authenticated user.
     *
     * @return email as String, or null if not authenticated
     */
    public static String getCurrentUserEmail() {
        CustomUserDetails userDetails = getCurrentUserDetails();
        return userDetails != null ? userDetails.getEmail() : null;
    }

    /**
     * Get the public user ID of the currently authenticated user.
     *
     * @return publicUserId as String, or null if not authenticated
     */
    public static String getCurrentPublicUserId() {
        CustomUserDetails userDetails = getCurrentUserDetails();
        return userDetails != null ? userDetails.getPublicUserId() : null;
    }

    /**
     * Get the internal user ID (database primary key) of the currently authenticated user.
     *
     * @return user ID as Long, or null if not authenticated
     */
    public static Long getCurrentUserId() {
        CustomUserDetails userDetails = getCurrentUserDetails();
        return userDetails != null ? userDetails.getUser().getUserId() : null;
    }

    /**
     * Get the full User entity of the currently authenticated user.
     *
     * <p><b>Warning:</b> This returns the User entity from the security context.
     * It may not reflect the latest database state. Use with caution.
     *
     * @return User entity or null if not authenticated
     */
    public static User getCurrentUser() {
        CustomUserDetails userDetails = getCurrentUserDetails();
        return userDetails != null ? userDetails.getUser() : null;
    }

    /**
     * Get the current user's role as an enum.
     *
     * @return Role enum or null if not authenticated
     */
    public static Role getCurrentUserRole() {
        User user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }

    /* ==========================================================
       AUTHENTICATION CHECK METHODS
       ========================================================== */

    /**
     * Check if the current user is authenticated (not anonymous).
     *
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Exclude anonymous users
        return !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * Check if the current user is active and email verified.
     *
     * @return true if a user is fully enabled, false otherwise
     */
    public static boolean isUserEnabled() {
        CustomUserDetails userDetails = getCurrentUserDetails();
        return userDetails != null && userDetails.isEnabled();
    }

    /* ==========================================================
       ROLE CHECK METHODS
       ========================================================== */

    /**
     * Check if the current user has a specific role.
     *
     * <p>Automatically prefixes a role with "ROLE_" if not already present.
     *
     * @param role the role name (e.g., "ADMIN" or "ROLE_ADMIN")
     * @return true if a user has the role, false otherwise
     */
    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            log.warn("hasRole called with null or blank role");
            return false;
        }

        Authentication authentication = getAuthentication();

        if (authentication == null) {
            return false;
        }

        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> roleWithPrefix.equals(authority.getAuthority()));
    }

    /**
     * Check if the current user has any of the specified roles.
     *
     * @param roles variable number of role names
     * @return true if a user has at least one role, false otherwise
     */
    public static boolean hasAnyRole(String... roles) {
        if (roles == null) {
            return false;
        }

        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the current user has all the specified roles.
     *
     * @param roles variable number of role names
     * @return true if a user has all roles, false otherwise
     */
    public static boolean hasAllRoles(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }

        for (String role : roles) {
            if (!hasRole(role)) {
                return false;
            }
        }

        return true;
    }

    /* ==========================================================
       AUTHORIZATION HELPER METHODS
       ========================================================== */

    /**
     * Verify that the current user matches the given public user ID.
     * Useful for authorization checks in controllers.
     *
     * @param publicUserId the public user ID to check
     * @return true if current user matches, false otherwise
     */
    public static boolean isCurrentUser(String publicUserId) {
        String currentPublicUserId = getCurrentPublicUserId();
        return currentPublicUserId != null && currentPublicUserId.equals(publicUserId);
    }

    /**
     * Verify that the current user matches the given user ID.
     * Useful for authorization checks with internal IDs.
     *
     * @param userId the user ID to check
     * @return true if current user matches, false otherwise
     */
    public static boolean isCurrentUser(Long userId) {
        Long currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(userId);
    }

    /**
     * Require that the user is authenticated, throw exception if not.
     *
     * @throws UnauthorizedException if not authenticated
     */
    public static void requireAuthenticated() {
        if (!isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    /**
     * Require that the user has a specific role, throw exception if not.
     *
     * @param role the required role
     * @throws InsufficientPermissionException if a user doesn't have the role
     */
    public static void requireRole(String role) {
        if (!hasRole(role)) {
            throw new InsufficientPermissionException("Required role: " + role);
        }
    }

    /**
     * Require that the user has any of the specified roles.
     *
     * @param roles variable number of role names
     * @throws InsufficientPermissionException if a user doesn't have any role
     */
    public static void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new InsufficientPermissionException(
                    "Required one of roles: " + String.join(", ", roles)
            );
        }
    }

    /**
     * Require that the current user matches the given user ID.
     *
     * @param userId the user ID to check
     * @throws InsufficientPermissionException if current user doesn't match
     */
    public static void requireCurrentUser(Long userId) {
        if (!isCurrentUser(userId)) {
            throw new InsufficientPermissionException("Access denied: not the current user");
        }
    }

    /**
     * Require that the current user matches the given public user ID.
     *
     * @param publicUserId the public user ID to check
     * @throws InsufficientPermissionException if current user doesn't match
     */
    public static void requireCurrentUser(String publicUserId) {
        if (!isCurrentUser(publicUserId)) {
            throw new InsufficientPermissionException("Access denied: not the current user");
        }
    }

    /* ==========================================================
       HTTP REQUEST UTILITIES
       ========================================================== */

    private static final String IPV4_PATTERN =
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$";

    /**
     * Extract the client IP address.
     *
     * <p>Forwarded headers ({@code X-Forwarded-For}, {@code X-Real-IP}) are only
     * honoured when the request's immediate peer is one of our own proxies — a
     * loopback address in local dev, or the private Docker network address of
     * Nginx in production. A client talking to the app directly can spoof those
     * headers, so in that case the real {@code remoteAddr} is returned instead.
     *
     * @param request HttpServletRequest
     * @return client IP address or "unknown" if not available
     */
    public static String getClientIP(HttpServletRequest request) {
        if (request == null) {
            log.warn("getClientIP called with null request");
            return "unknown";
        }

        String remoteAddr = request.getRemoteAddr();

        if (isPrivateOrLoopback(remoteAddr)) {
            String forwarded = firstValidForwardedIp(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"));
            if (forwarded != null) {
                return forwarded;
            }
        }

        return remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "unknown";
    }

    private static String firstValidForwardedIp(String... headers) {
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                continue;
            }
            for (String candidate : header.split(",")) {
                String ip = candidate.trim();
                if (isValidIp(ip)) {
                    return ip;
                }
            }
        }
        return null;
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (ip.indexOf(':') >= 0) {
            // Lenient IPv6 check — hex digits, colons, dots and an optional zone id.
            return ip.matches("^[0-9a-fA-F:.%]+$");
        }
        return ip.matches(IPV4_PATTERN);
    }

    private static boolean isPrivateOrLoopback(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String value = ip.trim();

        if (value.indexOf(':') >= 0) {
            int zone = value.indexOf('%');
            if (zone >= 0) {
                value = value.substring(0, zone);
            }
            if ("::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) {
                return true;
            }
            String lower = value.toLowerCase();
            // Unique local (fc00::/7) and link local (fe80::/10).
            return lower.startsWith("fc") || lower.startsWith("fd")
                    || lower.startsWith("fe8") || lower.startsWith("fe9")
                    || lower.startsWith("fea") || lower.startsWith("feb");
        }

        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }

        int first = octets[0];
        int second = octets[1];
        return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }

    /* ==========================================================
       INTERNAL HELPER METHODS
       ========================================================== */

    /**
     * Get the current Authentication object.
     *
     * @return Authentication object, or null if not available
     */
    private static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}