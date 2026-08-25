package com.afrochow.security;

import com.afrochow.security.service.CustomUserDetailsService;
import com.afrochow.security.util.CookieConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for removing the filter's hand-rolled PUBLIC_PATHS skip list. That
 * list previously short-circuited JWT processing on public routes, which also
 * meant an authenticated caller's cookie was ignored on paths that SecurityConfig
 * actually protected (e.g. DELETE /images). The filter must now run everywhere and
 * simply populate the security context when a valid cookie is present, leaving the
 * authorize-request rules in SecurityConfig as the single source of truth.
 */
class JwtAuthenticationFilterTest {

    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenProvider, userDetailsService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicPathWithValidCookie_stillAuthenticatesUser() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie(CookieConstants.ACCESS_TOKEN_COOKIE, "valid-jwt")
        });
        when(tokenProvider.isValidToken("valid-jwt")).thenReturn(true);
        when(tokenProvider.isTokenExpired("valid-jwt")).thenReturn(false);

        Date issuedAt = new Date();
        when(tokenProvider.extractUserInfo("valid-jwt")).thenReturn(
                new JwtTokenProvider.UserInfo(
                        "ade", "ade@example.com", "CUS1", "CUSTOMER",
                        issuedAt, new Date(issuedAt.getTime() + 60_000)));

        UserDetails userDetails = mock(UserDetails.class);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        doReturn(authorities).when(userDetails).getAuthorities();
        when(userDetails.isEnabled()).thenReturn(true);
        when(userDetails.isAccountNonLocked()).thenReturn(true);
        when(userDetails.isAccountNonExpired()).thenReturn(true);
        when(userDetails.isCredentialsNonExpired()).thenReturn(true);
        when(userDetailsService.loadUserByUsername("ade@example.com")).thenReturn(userDetails);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        verify(chain).doFilter(request, response);
    }

    @Test
    void requestWithoutCookie_continuesChainWithoutAuthenticating() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getCookies()).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
