package com.afrochow.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUtilsTest {

    @Test
    void nullRequest_returnsUnknown() {
        assertThat(SecurityUtils.getClientIP(null)).isEqualTo("unknown");
    }

    @Test
    void directPublicPeer_ignoresSpoofedForwardedHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
        when(request.getHeader("X-Real-IP")).thenReturn("5.6.7.8");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void privateProxyPeer_usesFirstValidForwardedIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.18.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9, 10.0.0.1");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void privateProxyPeer_fallsBackToRealIpHeaderWhenXffAbsent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.9");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void privateProxyPeer_invalidForwardedHeader_fallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("not-an-ip");
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void ipv6LoopbackPeer_trustsForwardedIpv6() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("::1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("2001:db8::1");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("2001:db8::1");
    }
}
