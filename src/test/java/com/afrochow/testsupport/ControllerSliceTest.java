package com.afrochow.testsupport;

import com.afrochow.security.JwtAuthenticationFilter;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotation for Phase 6 controller-slice tests.
 *
 * Bundles the two pieces of class-level boilerplate every {@code @WebMvcTest}
 * in this codebase needs:
 *
 * <ul>
 *   <li>{@code excludeFilters} for {@link JwtAuthenticationFilter} — without
 *       this, {@code @WebMvcTest}'s default component scan tries to
 *       instantiate the filter (and its {@code JwtTokenProvider}/
 *       {@code CustomUserDetailsService} constructor deps) even inside a
 *       narrow single-controller slice.</li>
 *   <li>{@code @AutoConfigureMockMvc(addFilters = false)} — these tests cover
 *       request routing, {@code @Valid} DTO validation, correct service
 *       invocation, and HTTP status / response-shape correctness via
 *       {@code GlobalExceptionHandler}. They deliberately do not exercise the
 *       security filter chain or {@code @PreAuthorize}/{@code @deptAccess}
 *       enforcement, which requires the full {@code SecurityConfig} +
 *       method-security aspect weaving that {@code @WebMvcTest} does not
 *       load.</li>
 * </ul>
 *
 * The remaining shared pieces — a mocked {@code CacheManager} and a
 * locally-built {@code ObjectMapper} — are fields, not annotation
 * attributes, so they live on {@link AbstractControllerTest} instead. Extend
 * that class alongside this annotation.
 *
 * Also imports {@link AbstractControllerTest.ArgumentResolverConfig}, which
 * registers Spring Security's real {@code AuthenticationPrincipalArgumentResolver}.
 * That resolver is normally wired in by the app's {@code @EnableWebSecurity}
 * config, which {@code @WebMvcTest}'s narrow scan doesn't load — and since
 * the config lives on the shared superclass rather than each concrete test
 * class, Spring Boot's nested-{@code @TestConfiguration} auto-detection
 * (which only looks at the class directly annotated with
 * {@code @WebMvcTest}) won't find it either, hence the explicit
 * {@code @Import} here.
 *
 * Usage:
 * <pre>
 *   {@literal @}ControllerSliceTest(CategoryController.class)
 *   class CategoryControllerTest extends AbstractControllerTest {
 *       {@literal @}MockitoBean private CategoryService categoryService;
 *       ...
 *   }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(AbstractControllerTest.ArgumentResolverConfig.class)
public @interface ControllerSliceTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
