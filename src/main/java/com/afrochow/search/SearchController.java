package com.afrochow.search;

import com.afrochow.common.ApiResponse;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "APIs for searching vendors, products, and categories")
public class SearchController {

    private final SearchService searchService;

    // ========== MARKET AVAILABILITY ==========

    @GetMapping("/market-status")
    @Operation(summary = "Is Afrochow active in this location",
            description = "Pass ?city= and/or ?lat=&lng= to check whether there's at least one active, " +
                    "verified vendor in that city or within a ~100km radius of those coordinates. Lets the " +
                    "frontend show an honest \"not in your area yet\" state instead of silently falling back " +
                    "to nationwide content for a market Afrochow doesn't actually operate in.")
    public ResponseEntity<ApiResponse<Boolean>> getMarketStatus(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        boolean served = searchService.isMarketServed(city, lat, lng);
        return ResponseEntity.ok(ApiResponse.success(served));
    }

    // ========== VENDOR SEARCH ==========

    @GetMapping("/vendors/{publicUserId}")
    @Operation(summary = "Get vendor details",
            description = "Get vendor profile details by public vendor ID. Pass ?lat=&lng= (optional) to have " +
                    "distanceKm from that point computed via the Redis vendor geo index.")
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> getVendorByPublicId(
            @PathVariable String publicUserId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        VendorProfileResponseDto vendor = searchService.getVendorByPublicId(publicUserId, lat, lng);
        return ResponseEntity.ok(ApiResponse.success("Vendor details retrieved successfully", vendor));
    }

    @GetMapping("/vendors/by-product")
    @Operation(summary = "Get vendors by product name",
            description = "Find verified vendors that carry products matching the search query")
    public ResponseEntity<ApiResponse<List<VendorProfileResponseDto>>> getVendorsByProductName(
            @RequestParam String query) {
        List<VendorProfileResponseDto> vendors = searchService.getVendorsByProductName(query);
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    @GetMapping("/vendors/city/{city}")
    @Operation(summary = "Get vendors by city", description = "Get all active vendors in a specific city")
    public ResponseEntity<ApiResponse<List<VendorProfileResponseDto>>> getVendorsByCity(@PathVariable String city) {
        List<VendorProfileResponseDto> vendors = searchService.getVendorsByCity(city);
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    @GetMapping("/vendors/top-rated")
    @Operation(summary = "Get top-rated vendors", description = "Get vendors with the most reviews")
    public ResponseEntity<ApiResponse<List<VendorProfileResponseDto>>> getTopRatedVendors() {
        List<VendorProfileResponseDto> vendors = searchService.getTopRatedVendors();
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    @GetMapping("/vendors/verified")
    @Operation(summary = "Get verified vendors", description = "Get all verified and active vendors")
    public ResponseEntity<ApiResponse<List<VendorProfileResponseDto>>> getVerifiedVendors() {
        List<VendorProfileResponseDto> vendors = searchService.getVerifiedVendors();
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    @GetMapping("/vendors/advanced")
    @Operation(summary = "Advanced vendor search", description = "Search vendors with multiple filters")
    public ResponseEntity<ApiResponse<List<VendorProfileResponseDto>>> advancedVendorSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String storeCategory,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean isVerified,
            @RequestParam(required = false) Boolean isOpenNow) {
        List<VendorProfileResponseDto> vendors = searchService.advancedVendorSearch(
                query, storeCategory, city, isVerified, isOpenNow);
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    // ========== PRODUCT SEARCH ==========

    @GetMapping("/products/featured")
    @Operation(summary = "Get featured products",
               description = "Admin-pinned products always included. Pass ?city= to filter algorithmic fill by vendor city. " +
                            "Pass ?lat=&lng= (optional) to have each product's distanceKm from that point computed via the " +
                            "Redis vendor geo index. Pass ?scheduleType=SAME_DAY or ADVANCE_ORDER (optional) to split the " +
                            "homepage into a \"ready to order\" rail vs a \"pre-order / advance notice\" rail.")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getFeaturedProducts(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) com.afrochow.common.enums.ScheduleType scheduleType) {
        List<ProductResponseDto> products = searchService.getFeaturedProducts(city, lat, lng, scheduleType);
        return ResponseEntity.ok(ApiResponse.success("Featured products retrieved successfully", products));
    }

    @GetMapping("/products/{publicProductId}/similar")
    @Operation(summary = "Get the same dish at other vendors",
            description = "Find this exact product (by name) sold by other active + verified vendors. " +
                    "Pass ?lat=&lng= (optional) to have each result's distanceKm computed via the Redis " +
                    "vendor geo index.")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getSimilarProducts(
            @PathVariable String publicProductId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        List<ProductResponseDto> products = searchService.getSimilarProducts(publicProductId, lat, lng);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/products/popular/names")
    @Operation(summary = "Get popular product names", description = "Get simple array of popular product names sorted by order count")
    public ResponseEntity<ApiResponse<List<String>>> getPopularProductNames(
            @RequestParam(defaultValue = "5") int limit) {
        List<String> productNames = searchService.getPopularProductNames(limit);
        return ResponseEntity.ok(ApiResponse.success("Popular product names retrieved successfully", productNames));
    }

    @GetMapping("/products/advanced")
    @Operation(summary = "Advanced product search",
               description = "Search products with multiple filters including vendor location. " +
                            "Supports pagination with page (0-indexed) and size parameters. " +
                            "Default: page=0, size=20. Max size=100.")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<ProductResponseDto>>> advancedProductSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean isVegetarian,
            @RequestParam(required = false) Boolean isVegan,
            @RequestParam(required = false) Boolean isGlutenFree,
            @RequestParam(required = false) com.afrochow.common.enums.ScheduleType scheduleType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int pageSize = Math.min(size, 100);

        ApiResponse.PageResponse<ProductResponseDto> paginatedProducts = searchService.advancedProductSearch(
                query, city, categoryId, minPrice, maxPrice, isVegetarian, isVegan, isGlutenFree, scheduleType, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", paginatedProducts));
    }

    @GetMapping("/vendors/near-coordinates")
    @Operation(summary = "Get vendors near coordinates",
            description = "Find verified active vendors within radiusKm of the given lat/lng, ordered by distance")
    public ResponseEntity<ApiResponse<List<VendorProfileResponseDto>>> getVendorsNearCoordinates(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "25") double radiusKm) {
        List<VendorProfileResponseDto> vendors = searchService.getVendorsNearCoordinates(lat, lng, radiusKm);
        return ResponseEntity.ok(ApiResponse.success("Vendors near coordinates retrieved", vendors));
    }

}
