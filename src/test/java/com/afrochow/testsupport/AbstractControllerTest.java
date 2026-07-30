package com.afrochow.testsupport;

import com.afrochow.AfrochowApplication;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.model.User;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Shared base class for Phase 6 {@link ControllerSliceTest} classes.
 *
 * Two quirks of this codebase's Spring Boot 4 + {@code @WebMvcTest} setup
 * apply to every controller slice, not just one:
 *
 * <ol>
 *   <li>{@link AfrochowApplication} carries {@code @EnableCaching} directly,
 *       which {@code @WebMvcTest} inherits as its
 *       {@code @SpringBootConfiguration} root. That registers a
 *       {@code CacheInterceptor} whose {@code afterSingletonsInstantiated()}
 *       requires a {@code CacheManager} bean to exist in the context — even
 *       when nothing in the slice is actually cached. Mock it here so every
 *       subclass gets it for free.</li>
 *   <li>{@code @WebMvcTest} does not reliably wire an auto-configured
 *       {@code ObjectMapper} bean into the sliced context in this setup.
 *       Building one locally avoids depending on
 *       {@code JacksonAutoConfiguration} being present in the slice, while
 *       still matching Spring Boot's default Jackson behavior (ISO-8601
 *       dates instead of timestamps).</li>
 *   <li>{@code @WebMvcTest}'s narrow component scan does not pick up the
 *       app's {@code @EnableWebSecurity} configuration class, so Spring
 *       Security's {@code AuthenticationPrincipalArgumentResolver} never
 *       gets registered in the slice. Without it, {@code @AuthenticationPrincipal}
 *       parameters silently fall through to Spring MVC's model-attribute
 *       binder, which instantiates them via their only constructor with
 *       null arguments instead of resolving from the
 *       {@code SecurityContextHolder} — the nested {@link ArgumentResolverConfig}
 *       registers the real resolver class directly so the test slice behaves
 *       like production.</li>
 * </ol>
 *
 * Also provides {@link #authenticatedAs(String, String...)}, a
 * {@link RequestPostProcessor} for controller endpoints that take a plain
 * {@code Authentication} method parameter (resolved by Spring MVC via
 * {@code HttpServletRequest.getUserPrincipal()}). This sets the mock
 * request's principal directly, so it works without pulling in
 * {@code spring-security-test} or running the security filter chain (which
 * {@code @AutoConfigureMockMvc(addFilters = false)} disables anyway).
 */
public abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected CacheManager cacheManager;

    protected final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * Sets the mock request's principal to an {@link Authentication} for
     * {@code username}, so controller methods that take an
     * {@code Authentication} parameter (and call {@code getName()} on it)
     * resolve as expected. Roles are informational only in this slice —
     * {@code @PreAuthorize} is not woven here, see {@link ControllerSliceTest}.
     */
    protected static RequestPostProcessor authenticatedAs(String username, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        return request -> {
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    /**
     * Same idea as {@link #authenticatedAs(String, String...)}, but for
     * controller endpoints that use {@code @AuthenticationPrincipal
     * CustomUserDetails} instead of a plain {@code Authentication} parameter.
     * {@code @AuthenticationPrincipal} resolves via
     * {@code SecurityContextHolder.getContext().getAuthentication().getPrincipal()}
     * — a different path from {@code HttpServletRequest.getUserPrincipal()} —
     * so it needs the SecurityContext populated directly, not just the mock
     * request. {@link #clearSecurityContext()} resets that ThreadLocal after
     * each test so it can't leak into the next one.
     */
    protected static RequestPostProcessor authenticatedAsPrincipal(User user, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        CustomUserDetails principal = new CustomUserDetails(user, authorities);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Registers the real {@link AuthenticationPrincipalArgumentResolver} for
     * this test slice. Spring Boot only auto-detects nested
     * {@code @TestConfiguration} classes when they're declared directly on
     * the class annotated with {@code @WebMvcTest} — since this one lives on
     * the shared superclass instead, {@link ControllerSliceTest} pulls it in
     * explicitly via {@code @Import} rather than relying on auto-detection.
     */
    @TestConfiguration
    public static class ArgumentResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }
}
