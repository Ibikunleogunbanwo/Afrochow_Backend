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
import org.springframework.transaction.annotation.Transactional;

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
     * Optionally filter by search query (case-insensitive, matches product name,
     * vendor name, or category name — returns closest match first).
     * Featured products always appear first within results.
     */
    @GetMapping
    @Operation(
            summary = "List / search all products",
            description = "Paginated list of all products for admin management. Pass `search` to filter by name, vendor, or category."
    )
    public ResponseEntity<ApiResponse<Page<AdminProductSummary>>> getAllProducts(
            @RequestParam(required = false)    String search,
            @RequestParam(required = false)    Boolean featured,
            @RequestParam(required = false)    Boolean adminVisible,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable   = PageRequest.of(page, size);
        boolean hasSearch       = search != null && !search.isBlank();
        boolean hasFeatured     = featured != null;
        boolean hasAdminVisible = adminVisible != null;

        Page<AdminProductSummary> result;
        if (hasSearch && hasFeatured) {
            result = productRepository.searchForAdminWithFeatured(search.trim(), featured, pageable).map(this::toAdminSummary);
        } else if (hasSearch && hasAdminVisible) {
            result = productRepository.searchForAdminWithAdminVisible(search.trim(), adminVisible, pageable).map(this::toAdminSummary);
        } else if (hasSearch) {
            result = productRepository.searchForAdmin(search.trim(), pageable).map(this::toAdminSummary);
        } else if (hasFeatured) {
            result = productRepository.findByIsFeaturedOrderByFeaturedAtDesc(featured, pageable).map(this::toAdminSummary);
        } else if (hasAdminVisible) {
            result = productRepository.findByAdminVisible(adminVisible, pageable).map(this::toAdminSummary);
        } else {
            result = productRepository.findAllForAdmin(pageable).map(this::toAdminSummary);
        }

        return ResponseEntity.ok(ApiResponse.success("Products retrieved", result));
    }

    private AdminProductSummary toAdminSummary(com.afrochow.product.model.Product p) {
        return new AdminProductSummary(
                p.getPublicProductId(),
                p.getName(),
                p.getPrice(),
                p.getImageUrl(),
                p.getAvailable(),
                p.getAdminVisible(),
                p.getVendor() != null ? p.getVendor().getRestaurantName() : null,
                p.getVendor() != null ? p.getVendor().getPublicVendorId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getIsFeatured(),
                p.getFeaturedAt()
        );
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

    /**
     * Unpin all currently featured products in one operation.
     * Used by the "Clear All Featured" button in the admin dashboard.
     */
    @DeleteMapping("/featured/clear")
    @Transactional
    @Operation(summary = "Clear all featured products", description = "Unpin every product from the featured section")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearAllFeatured() {
        List<Product> pinned = productRepository.findAdminFeaturedProducts();
        pinned.forEach(p -> {
            p.setIsFeatured(false);
            p.setFeaturedAt(null);
        });
        productRepository.saveAll(pinned);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cleared", pinned.size());

        return ResponseEntity.ok(ApiResponse.success(
                pinned.size() + " product(s) removed from featured section", data));
    }

    // ========== PRODUCT VISIBILITY & DELETION ==========

    /**
     * Toggle a product's availability (visible ↔ hidden from customers).
     * Does NOT delete the product — the vendor can re-enable it from their dashboard.
     */
    @Transactional
    @PatchMapping("/{publicProductId}/visibility")
    @Operation(summary = "Toggle product visibility",
               description = "Hide or show a product without deleting it. Hidden products are invisible to customers.")
    public ResponseEntity<ApiResponse<AdminProductSummary>> toggleVisibility(
            @PathVariable String publicProductId) {

        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + publicProductId));

        product.setAdminVisible(!Boolean.TRUE.equals(product.getAdminVisible()));
        productRepository.save(product);

        String msg = Boolean.TRUE.equals(product.getAdminVisible())
                ? "Product is now visible to customers"
                : "Product has been suspended from customer view";

        return ResponseEntity.ok(ApiResponse.success(msg, toSummary(product)));
    }

    /**
     * Permanently delete a product. This action is irreversible.
     * Prefer toggling visibility if you only want to hide it temporarily.
     */
    @Transactional
    @DeleteMapping("/{publicProductId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Delete product (SUPERADMIN only)",
               description = "Permanently deletes a product. Irreversible. Use visibility toggle to hide temporarily.")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable String publicProductId) {

        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + publicProductId));

        productRepository.delete(product);
        return ResponseEntity.ok(ApiResponse.success("Product permanently deleted"));
    }

    /** Map a Product entity to the summary DTO. */
    private AdminProductSummary toSummary(Product product) {
        return new AdminProductSummary(
                product.getPublicProductId(),
                product.getName(),
                product.getPrice(),
                product.getImageUrl(),
                product.getAvailable(),
                product.getAdminVisible(),
                product.getVendor() != null ? product.getVendor().getRestaurantName() : null,
                product.getVendor() != null ? product.getVendor().getPublicVendorId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getIsFeatured(),
                product.getFeaturedAt()
        );
    }

    // ========== DTOs ==========

    public record AdminProductSummary(
            String publicProductId,
            String name,
            java.math.BigDecimal price,
            String imageUrl,
            Boolean available,       // vendor-controlled
            Boolean adminVisible,    // platform-controlled — false = admin-suspended
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
