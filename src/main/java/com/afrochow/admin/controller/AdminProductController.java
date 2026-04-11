package com.afrochow.admin.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@Tag(name = "Admin Product Management", description = "Admin APIs for managing products and featured curation")
public class AdminProductController {

    private final ProductRepository productRepository;

    // ========== ALL PRODUCTS LIST ==========

    /**
     * Paginated list of ALL products for the admin product management page.
     * Featured products appear first, then newest. Includes unavailable products.
     * Each product includes isFeatured so the frontend toggle knows its current state.
     */
    @GetMapping
    @Operation(summary = "List all products", description = "Paginated list of all products for admin management. Featured products appear first.")
    public ResponseEntity<ApiResponse<Page<AdminProductSummary>>> getAllProducts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AdminProductSummary> result = productRepository.findAllForAdmin(pageable)
                .map(p -> new AdminProductSummary(
                        p.getPublicProductId(),
                        p.getName(),
                        p.getPrice(),
                        p.getImageUrl(),
                        p.getAvailable(),
                        p.getVendor() != null ? p.getVendor().getRestaurantName() : null,
                        p.getVendor() != null ? p.getVendor().getPublicVendorId() : null,
                        p.getCategory() != null ? p.getCategory().getName() : null,
                        p.getIsFeatured(),
                        p.getFeaturedAt()
                ));

        return ResponseEntity.ok(ApiResponse.success("Products retrieved", result));
    }

    // ========== FEATURED MANAGEMENT ==========

    /**
     * Toggle a product's featured status.
     * If currently featured, it will be unpinned (returns to algorithmic ranking).
     * If currently unfeatured, it will be pinned (always appears in featured section).
     */
    @PutMapping("/{publicProductId}/toggle-feature")
    @Operation(summary = "Toggle product featured status", description = "Pin or unpin a product from the featured section")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFeature(
            @PathVariable String publicProductId) {

        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found: " + publicProductId));

        boolean nowFeatured = !Boolean.TRUE.equals(product.getIsFeatured());

        product.setIsFeatured(nowFeatured);
        product.setFeaturedAt(nowFeatured ? LocalDateTime.now() : null);
        productRepository.save(product);

        String message = nowFeatured ? "Product pinned to featured section" : "Product removed from featured section";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("publicProductId", product.getPublicProductId());
        data.put("name",            product.getName());
        data.put("isFeatured",      nowFeatured);
        data.put("featuredAt",      product.getFeaturedAt());

        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    /**
     * Get all currently admin-pinned featured products.
     */
    @GetMapping("/featured")
    @Operation(summary = "Get admin-featured products", description = "List all manually pinned featured products")
    public ResponseEntity<ApiResponse<List<FeaturedProductSummary>>> getAdminFeaturedProducts() {
        List<FeaturedProductSummary> featured = productRepository.findAdminFeaturedProducts()
                .stream()
                .map(p -> new FeaturedProductSummary(
                        p.getPublicProductId(),
                        p.getName(),
                        p.getPrice(),
                        p.getImageUrl(),
                        p.getVendor().getRestaurantName(),
                        p.getVendor().getPublicVendorId(),
                        p.getFeaturedAt()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                "Admin featured products retrieved", featured));
    }

    // ========== DTOs ==========

    public record AdminProductSummary(
            String publicProductId,
            String name,
            java.math.BigDecimal price,
            String imageUrl,
            Boolean available,
            String vendorName,
            String publicVendorId,
            String categoryName,
            Boolean isFeatured,
            LocalDateTime featuredAt
    ) {}

    public record FeaturedProductSummary(
            String publicProductId,
            String name,
            java.math.BigDecimal price,
            String imageUrl,
            String restaurantName,
            String publicVendorId,
            LocalDateTime featuredAt
    ) {}
}
