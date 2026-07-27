package com.afrochow.product.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.common.ResponseBuilder;
import com.afrochow.product.dto.ProductRequestDto;
import com.afrochow.product.dto.ProductUpdateRequestDto;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.dto.ProductSummaryResponseDto;
import com.afrochow.product.service.ProductService;
import com.afrochow.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Controller for product management
 *
 * Public endpoints (GET):
 * - GET /products/{publicProductId} - Get product details
 * - GET /products/vendor/{publicVendorId} - Get vendor's products
 * - GET /products/category/{categoryId} - Get products by category
 *
 * (Plain listing, search, and price/dietary filtering used to live here too,
 * but had no frontend callers — /search/products/advanced covers all of that
 * with pagination and combined filters. Removed as dead code.)
 *
 * Vendor endpoints (requires VENDOR role):
 * - POST /vendor/products - Create new product
 * - GET /vendor/products - Get my products
 * - GET /vendor/products/{publicProductId} - Get my product details
 * - PUT /vendor/products/{publicProductId} - Update product
 * - DELETE /vendor/products/{publicProductId} - Delete product
 * - PATCH /vendor/products/{publicProductId}/availability - Toggle availability
 * - POST /vendor/products/{publicProductId}/image - Upload product image
 */
@RestController
@Tag(name = "Products", description = "Product management endpoints")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ========== PUBLIC ENDPOINTS (no authentication required) ==========

    /**
     * Get product by public ID
     */
    @GetMapping("/products/{publicProductId}")
    @Operation(summary = "Get product", description = "Get product details by public ID")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProduct(@PathVariable String publicProductId) {
        ProductResponseDto product = productService.getProductByPublicId(publicProductId);
        return ResponseBuilder.ok("Product retrieved successfully", product);
    }

    /**
     * Get products by vendor
     */
    @GetMapping("/products/vendor/{publicVendorId}")
    @Operation(summary = "Get vendor products", description = "Get all products for a specific vendor")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponseDto>>> getVendorProducts(
            @PathVariable String publicVendorId,
            @RequestParam(defaultValue = "true") Boolean availableOnly
    ) {
        List<ProductSummaryResponseDto> products = productService.getProductsByVendor(publicVendorId, availableOnly);
        return ResponseBuilder.ok("Vendor products retrieved successfully", products);
    }

    /**
     * Get products by category
     */
    @GetMapping("/products/category/{categoryId}")
    @Operation(summary = "Get products by category", description = "Get all products in a specific category")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponseDto>>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "true") Boolean availableOnly
    ) {
        List<ProductSummaryResponseDto> products = productService.getProductsByCategory(categoryId, availableOnly);
        return ResponseBuilder.ok("Products retrieved successfully", products);
    }

    // ========== VENDOR ENDPOINTS (requires VENDOR role) ==========

    /**
     * Create new product (vendor only)
     */
    @PostMapping("/vendor/products")
    @Operation(summary = "Create product", description = "Create a new product (verified vendors only)")
    public ResponseEntity<ApiResponse<ProductResponseDto>> createProduct(
            @Valid @RequestBody ProductRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        ProductResponseDto product = productService.createProduct(username, request);
        return ResponseBuilder.created("Product created successfully", product);
    }

    /**
     * Get all products for authenticated vendor
     */
    @GetMapping("/vendor/products")
    @Operation(summary = "Get my products", description = "Get all products for the authenticated vendor")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        List<ProductResponseDto> products = productService.getVendorProducts(username);
        return ResponseBuilder.ok("Your products retrieved successfully", products);
    }

    /**
     * Get specific product for authenticated vendor
     */
    @GetMapping("/vendor/products/{publicProductId}")
    @Operation(summary = "Get my product", description = "Get specific product details (ownership required)")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getMyProduct(
            @PathVariable String publicProductId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        ProductResponseDto product = productService.getVendorProduct(userId, publicProductId);
        return ResponseBuilder.ok("Product retrieved successfully", product);
    }

    /**
     * Update product (vendor only)
     */
    @PutMapping("/vendor/products/{publicProductId}")
    @Operation(summary = "Update product", description = "Update product details (ownership required)")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable String publicProductId,
            @Valid @RequestBody ProductUpdateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        ProductResponseDto product = productService.updateProduct(username, publicProductId, request);
        return ResponseBuilder.ok("Product updated successfully", product);
    }

    /**
     * Delete product (vendor only)
     */
    @DeleteMapping("/vendor/products/{publicProductId}")
    @Operation(summary = "Delete product", description = "Delete a product (ownership required)")
    public ResponseEntity<ApiResponse<String>> deleteProduct(
            @PathVariable String publicProductId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        productService.deleteProduct(username, publicProductId);
        return ResponseBuilder.ok("Product deleted successfully");
    }

    /**
     * Toggle product availability (vendor only)
     */
    @PatchMapping("/vendor/products/{publicProductId}/availability")
    @Operation(summary = "Toggle availability", description = "Toggle product availability (ownership required)")
    public ResponseEntity<ApiResponse<ProductResponseDto>> toggleAvailability(
            @PathVariable String publicProductId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        ProductResponseDto product = productService.toggleProductAvailability(userId, publicProductId);
        return ResponseBuilder.ok("Product availability toggled successfully", product);
    }

    /**
     * Upload product image (vendor only)
     */
    @PostMapping(value = "/vendor/products/{publicProductId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload image", description = "Upload product image (JPEG, PNG, GIF, WEBP - max 5MB)")
    public ResponseEntity<ApiResponse<ProductResponseDto>> uploadProductImage(
            @PathVariable String publicProductId,
            @Parameter(
                    description = "Product image file",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            Long userId = userDetails.getUserId();
            ProductResponseDto product = productService.uploadProductImage(userId, publicProductId, image);
            return ResponseBuilder.ok("Product image uploaded successfully", product);
        } catch (IOException e) {
            return ResponseBuilder.internalError("Failed to upload image: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseBuilder.badRequest(e.getMessage());
        }
    }
}
