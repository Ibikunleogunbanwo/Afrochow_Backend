package com.afrochow.search;

import com.afrochow.category.dto.CategoryResponseDto;
import com.afrochow.common.ApiResponse;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.search.dto.PopularCuisineDto;
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

    // ========== UNIVERSAL SEARCH ==========

    @GetMapping
    @Operation(summary = "Universal search", description = "Search across vendors, products, and categories")
    public ResponseEntity<SearchService.UniversalSearchResults> searchAll(@RequestParam String query) {
        SearchService.UniversalSearchResults results = searchService.searchAll(query);
        return ResponseEntity.ok(results);
    }

    // ========== VENDOR SEARCH ==========

    @GetMapping("/vendors/{publicUserId}")
    @Operation(summary = "Get vendor details", description = "Get vendor profile details by public vendor ID")
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> getVendorByPublicId(@PathVariable String publicUserId) {
        VendorProfileResponseDto vendor = searchService.getVendorByPublicId(publicUserId);
        return ResponseEntity.ok(ApiResponse.success("Vendor details retrieved successfully", vendor));
    }

    @GetMapping("/vendors")
    @Operation(summary = "Search vendors", description = "Search for vendors by name or cuisine type")
    public ResponseEntity<List<VendorProfileResponseDto>> searchVendors(@RequestParam String query) {
        List<VendorProfileResponseDto> vendors = searchService.searchVendors(query);
        return ResponseEntity.ok(vendors);
    }

    @GetMapping("/vendors/cuisine/{cuisineType}")
    @Operation(summary = "Get vendors by cuisine", description = "Get all vendors offering a specific cuisine type")
    public ResponseEntity<List<VendorProfileResponseDto>> getVendorsByCuisine(@PathVariable String cuisineType) {
        List<VendorProfileResponseDto> vendors = searchService.getVendorsByCuisine(cuisineType);
        return ResponseEntity.ok(vendors);
    }

    @GetMapping("/vendors/city/{city}")
    @Operation(summary = "Get vendors by city", description = "Get all active vendors in a specific city")
    public ResponseEntity<List<VendorProfileResponseDto>> getVendorsByCity(@PathVariable String city) {
        List<VendorProfileResponseDto> vendors = searchService.getVendorsByCity(city);
        return ResponseEntity.ok(vendors);
    }

    @GetMapping("/vendors/open")
    @Operation(summary = "Get open vendors", description = "Get all currently open vendors")
    public ResponseEntity<List<VendorProfileResponseDto>> getOpenVendors() {
        List<VendorProfileResponseDto> vendors = searchService.getOpenVendors();
        return ResponseEntity.ok(vendors);
    }

    @GetMapping("/vendors/top-rated")
    @Operation(summary = "Get top-rated vendors", description = "Get vendors with the most reviews")
    public ResponseEntity<List<VendorProfileResponseDto>> getTopRatedVendors() {
        List<VendorProfileResponseDto> vendors = searchService.getTopRatedVendors();
        return ResponseEntity.ok(vendors);
    }

    @GetMapping("/vendors/verified")
    @Operation(summary = "Get verified vendors", description = "Get all verified and active vendors")
    public ResponseEntity<List<VendorProfileResponseDto>> getVerifiedVendors() {
        List<VendorProfileResponseDto> vendors = searchService.getVerifiedVendors();
        return ResponseEntity.ok(vendors);
    }

    @GetMapping("/cuisines/popular")
    @Operation(summary = "Get popular cuisines",
               description = "Get list of cuisines with vendor count, total orders, and average rating, sorted by popularity")
    public ResponseEntity<ApiResponse<List<PopularCuisineDto>>> getPopularCuisines() {
        List<PopularCuisineDto> popularCuisines = searchService.getPopularCuisines();
        return ResponseEntity.ok(ApiResponse.success("Popular cuisines retrieved successfully", popularCuisines));
    }

    @GetMapping("/vendors/advanced")
    @Operation(summary = "Advanced vendor search", description = "Search vendors with multiple filters")
    public ResponseEntity<List<VendorProfileResponseDto>> advancedVendorSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String cuisineType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean isVerified,
            @RequestParam(required = false) Boolean isOpenNow) {
        List<VendorProfileResponseDto> vendors = searchService.advancedVendorSearch(
                query, cuisineType, city, isVerified, isOpenNow);
        return ResponseEntity.ok(vendors);
    }

    // ========== PRODUCT SEARCH ==========

    @GetMapping("/products")
    @Operation(summary = "Search products", description = "Search for products by name or description")
    public ResponseEntity<List<ProductResponseDto>> searchProducts(@RequestParam String query) {
        List<ProductResponseDto> products = searchService.searchProducts(query);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/name")
    @Operation(summary = "Search products by name", description = "Search for available products by name only")
    public ResponseEntity<List<ProductResponseDto>> searchProductsByName(@RequestParam String name) {
        List<ProductResponseDto> products = searchService.searchProductsByName(name);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/category/{categoryId}")
    @Operation(summary = "Get products by category", description = "Get all available products in a category")
    public ResponseEntity<List<ProductResponseDto>> getProductsByCategory(@PathVariable Long categoryId) {
        List<ProductResponseDto> products = searchService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/price-range")
    @Operation(summary = "Get products by price range", description = "Get products within a specific price range")
    public ResponseEntity<List<ProductResponseDto>> getProductsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        List<ProductResponseDto> products = searchService.getProductsByPriceRange(minPrice, maxPrice);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/vegetarian")
    @Operation(summary = "Get vegetarian products", description = "Get all available vegetarian products")
    public ResponseEntity<List<ProductResponseDto>> getVegetarianProducts() {
        List<ProductResponseDto> products = searchService.getVegetarianProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/vegan")
    @Operation(summary = "Get vegan products", description = "Get all available vegan products")
    public ResponseEntity<List<ProductResponseDto>> getVeganProducts() {
        List<ProductResponseDto> products = searchService.getVeganProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/gluten-free")
    @Operation(summary = "Get gluten-free products", description = "Get all available gluten-free products")
    public ResponseEntity<List<ProductResponseDto>> getGlutenFreeProducts() {
        List<ProductResponseDto> products = searchService.getGlutenFreeProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/popular")
    @Operation(summary = "Get popular products", description = "Get top 20 popular products by order count")
    public ResponseEntity<List<ProductResponseDto>> getPopularProducts() {
        List<ProductResponseDto> products = searchService.getPopularProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/chef-specials")
    @Operation(summary = "Get chef's specials",
               description = "Get top 10 chef's special products from African Kitchen or African Soups categories, sorted by popularity and rating")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getChefSpecials() {
        List<ProductResponseDto> products = searchService.getChefSpecials();
        return ResponseEntity.ok(ApiResponse.success("Chef's specials retrieved successfully", products));
    }

    @GetMapping("/products/featured")
    @Operation(summary = "Get featured products",
               description = "Get top 8 featured products from verified vendors, sorted by order count and reviews")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getFeaturedProducts() {
        List<ProductResponseDto> products = searchService.getFeaturedProducts();
        return ResponseEntity.ok(ApiResponse.success("Featured products retrieved successfully", products));
    }

    @GetMapping("/products/near-me")
    @Operation(summary = "Get products from best restaurants near me",
               description = "Get top 12 products from best-rated restaurants in the specified city, sorted by restaurant rating and popularity")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getProductsFromBestRestaurantsNearMe(
            @RequestParam String city) {
        List<ProductResponseDto> products = searchService.getProductsFromBestRestaurantsNearMe(city);
        return ResponseEntity.ok(ApiResponse.success("Products from best restaurants near you retrieved successfully", products));
    }

    @GetMapping("/products/monthly-popular")
    @Operation(summary = "Get most popular products (last 3 months)",
               description = "Get top 10 products with the most orders in the last 3 months, sorted by order count. " +
                            "Covers 3-month period to ensure enough data for MVP. " +
                            "Optionally filter by city using the 'city' query parameter.")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getMostPopularProductsThisMonth(
            @RequestParam(required = false) String city) {
        List<ProductResponseDto> products = searchService.getMostPopularProductsThisMonth(city);
        return ResponseEntity.ok(ApiResponse.success("Most popular products retrieved successfully", products));
    }

    @GetMapping("/products/popular/names")
    @Operation(summary = "Get popular product names", description = "Get simple array of popular product names sorted by order count")
    public ResponseEntity<ApiResponse<List<String>>> getPopularProductNames(
            @RequestParam(defaultValue = "5") int limit) {
        List<String> productNames = searchService.getPopularProductNames(limit);
        return ResponseEntity.ok(ApiResponse.success("Popular product names retrieved successfully", productNames));
    }

    @GetMapping("/products/top-rated")
    @Operation(summary = "Get top-rated products", description = "Get top 20 products with most reviews")
    public ResponseEntity<List<ProductResponseDto>> getTopRatedProducts() {
        List<ProductResponseDto> products = searchService.getTopRatedProducts();
        return ResponseEntity.ok(products);
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Cap max page size at 100
        int pageSize = Math.min(size, 100);

        ApiResponse.PageResponse<ProductResponseDto> paginatedProducts = searchService.advancedProductSearch(
                query, city, categoryId, minPrice, maxPrice, isVegetarian, isVegan, isGlutenFree, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", paginatedProducts));
    }

    // ========== CATEGORY SEARCH ==========

    @GetMapping("/categories")
    @Operation(summary = "Search categories", description = "Search for categories by name")
    public ResponseEntity<List<CategoryResponseDto>> searchCategories(@RequestParam String query) {
        List<CategoryResponseDto> categories = searchService.searchCategories(query);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/categories/all")
    @Operation(summary = "Get all active categories", description = "Get all active categories ordered by display order")
    public ResponseEntity<List<CategoryResponseDto>> getAllActiveCategories() {
        List<CategoryResponseDto> categories = searchService.getAllActiveCategories();
        return ResponseEntity.ok(categories);
    }
}
