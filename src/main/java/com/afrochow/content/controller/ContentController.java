package com.afrochow.content.controller;

import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.common.response.ApiResponse;
import com.afrochow.common.response.ResponseBuilder;
import com.afrochow.content.model.FaqDocument;
import com.afrochow.content.model.PolicyDocument;
import com.afrochow.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Static help-centre and policy content.
 *
 * Public endpoints (no auth required — covered by the {@code /public/**} rule in SecurityConfig):
 * - GET /public/content/faqs/{audience} - Customer or vendor FAQs
 * - GET /public/content/policies/{slug} - privacy, terms or cookies
 *
 * Admin endpoints (covered by the {@code /admin/**} rule in SecurityConfig):
 * - GET /admin/content/faqs - Admin dashboard help
 */
@RestController
@Tag(name = "Content", description = "Help centre FAQs and policy pages")
public class ContentController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/public/content/faqs/{audience}")
    @Operation(summary = "Get FAQs", description = "Get help-centre FAQs for the customer or vendor audience")
    public ResponseEntity<ApiResponse<FaqDocument>> getPublicFaqs(@PathVariable String audience) {
        // Admin help documents internal tooling, so it is only served from the authenticated admin route.
        if (ContentService.ADMIN_AUDIENCE.equals(audience)) {
            throw new ResourceNotFoundException("FAQ document", "audience", audience);
        }
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .body(ApiResponse.success("FAQs retrieved successfully", contentService.getFaqs(audience)));
    }

    @GetMapping("/admin/content/faqs")
    @Operation(summary = "Get admin help", description = "Get the admin dashboard help FAQs")
    public ResponseEntity<ApiResponse<FaqDocument>> getAdminFaqs() {
        return ResponseBuilder.ok("FAQs retrieved successfully",
                contentService.getFaqs(ContentService.ADMIN_AUDIENCE));
    }

    @GetMapping("/public/content/policies/{slug}")
    @Operation(summary = "Get policy", description = "Get a policy page: privacy, terms or cookies")
    public ResponseEntity<ApiResponse<PolicyDocument>> getPolicy(@PathVariable String slug) {
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .body(ApiResponse.success("Policy retrieved successfully", contentService.getPolicy(slug)));
    }
}
