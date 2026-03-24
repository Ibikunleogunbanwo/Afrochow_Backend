package com.afrochow.security;

import com.afrochow.common.exceptions.JwtAuthenticationException;
import com.afrochow.common.exceptions.JwtExpiredTokenException;
import com.afrochow.security.Services.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.afrochow.security.Utils.CookieConstants.ACCESS_TOKEN_COOKIE;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/public/",
            "/api/images",                   // DELETE /api/images?imageUrl=...
            "/api/images/",                  // GET /api/images/category/filename
            "/swagger-ui/",
            "/v3/api-docs",
            "/error",
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/verify-email",
            "/auth/resend-verification",
            "/public/",
            "/images",
            "/images/",
            "/stats/**"
            // NOTE: Promotion paths are intentionally NOT listed here.
            // The JWT filter must run for all /promotions/** requests so that
            // vendor/admin/authenticated rules in SecurityConfig can evaluate
            // the caller's role. For truly public promotion endpoints, SecurityConfig
            // already grants permitAll() — but only after the filter populates the
            // security context (or leaves it anonymous, which is fine for permitAll).
    );

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   CustomUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            if (!isAlreadyAuthenticated()) {
                String jwtToken = extractTokenFromCookie(request);

                if (StringUtils.hasText(jwtToken)) {
                    logger.debug("Extracted JWT token from cookie '{}'", ACCESS_TOKEN_COOKIE);

                    if (tokenProvider.isValidToken(jwtToken) && !tokenProvider.isTokenExpired(jwtToken)) {
                        authenticateUser(jwtToken, request);
                    }
                }
            }

        } catch (JwtExpiredTokenException e) {
            handleExpiredToken(e);
        } catch (JwtAuthenticationException e) {
            handleAuthenticationFailure(e);
        } catch (Exception e) {
            logger.error("Unexpected authentication error: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        } finally {
            filterChain.doFilter(request, response);
        }
    }

    // ------------------- COOKIE EXTRACTION -------------------

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // ------------------- AUTHENTICATION LOGIC -------------------

    private void authenticateUser(String jwtToken, HttpServletRequest request) {
        JwtTokenProvider.UserInfo userInfo = tokenProvider.extractUserInfo(jwtToken);

        if (userInfo.expiration().before(new java.util.Date())) {
            throw new JwtExpiredTokenException("Token expired for user: " + userInfo.username(),
                    userInfo.expiration(),
                    userInfo.issuedAt());
        }

        UserDetails userDetails = loadUserDetails(userInfo.email());
        validateAccountStatus(userDetails, userInfo.username());
        validateRoleConsistency(userDetails, userInfo);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        logger.debug("Authenticated user '{}' via cookie JWT", userInfo.username());
    }

    private UserDetails loadUserDetails(String email) {
        try {
            return userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            throw new JwtAuthenticationException(
                    JwtAuthenticationException.JwtErrorCode.USER_NOT_FOUND,
                    "User not found: " + email,
                    e
            );
        }
    }

    private void validateAccountStatus(UserDetails userDetails, String username) {
        if (!userDetails.isEnabled()) throw new JwtAuthenticationException(
                JwtAuthenticationException.JwtErrorCode.ACCOUNT_DISABLED,
                "Account disabled: " + username);
        if (!userDetails.isAccountNonLocked()) throw new JwtAuthenticationException(
                JwtAuthenticationException.JwtErrorCode.ACCOUNT_LOCKED,
                "Account locked: " + username);
        if (!userDetails.isAccountNonExpired()) throw new JwtAuthenticationException(
                JwtAuthenticationException.JwtErrorCode.ACCOUNT_EXPIRED,
                "Account expired: " + username);
        if (!userDetails.isCredentialsNonExpired()) throw new JwtAuthenticationException(
                JwtAuthenticationException.JwtErrorCode.CREDENTIALS_EXPIRED,
                "Credentials expired: " + username);
    }

    private void validateRoleConsistency(UserDetails userDetails, JwtTokenProvider.UserInfo userInfo) {
        boolean roleMatches = userDetails.getAuthorities().stream()
                .anyMatch(auth -> Objects.equals(auth.getAuthority(), "ROLE_" + userInfo.role()));
        if (!roleMatches) throw new JwtAuthenticationException(
                JwtAuthenticationException.JwtErrorCode.ROLE_MISMATCH,
                "Role mismatch for user: " + userInfo.username());
    }

    /**
     * Returns true only if the request already has a REAL authenticated user.
     * Ignores Spring's anonymous placeholder token, which is always present
     * before authentication and would otherwise cause this filter to skip
     * JWT validation entirely.
     */
    private boolean isAlreadyAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null &&
                auth.isAuthenticated() &&
                !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * Skip JWT processing entirely for public paths.
     * Uses startsWith so /api/images/ covers /api/images/VendorProfileImage/abc.png etc.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        if (isPublic) {
            logger.debug("Skipping JWT filter for public path: {}", path);
        }
        return isPublic;
    }

    private void handleExpiredToken(JwtExpiredTokenException e) {
        logger.warn("Token expired: {}", e.getMessage());
        SecurityContextHolder.clearContext();
    }

    private void handleAuthenticationFailure(JwtAuthenticationException e) {
        logger.warn("JWT authentication failed: {} (Error Code: {})",
                e.getMessage(), e.getErrorCode());
        SecurityContextHolder.clearContext();
    }
}