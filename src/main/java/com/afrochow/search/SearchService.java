package com.afrochow.search;

import com.afrochow.category.dto.CategoryResponseDto;
import com.afrochow.category.model.Category;
import com.afrochow.category.repository.CategoryRepository;
import com.afrochow.common.ApiResponse;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.search.dto.PopularCuisineDto;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.VendorMapper;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final VendorProfileRepository vendorProfileRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final VendorMapper vendorMapper;

    // ========== UNIVERSAL SEARCH ==========

    /**
     * Universal search across vendors, products, and categories
     */
    @Transactional(readOnly = true)
    public UniversalSearchResults searchAll(String query) {
        return UniversalSearchResults.builder()
                .vendors(searchVendors(query))
                .products(searchProducts(query))
                .categories(searchCategories(query))
                .build();
    }

    // ========== VENDOR SEARCH ==========

    /**
     * Get vendor by public vendor ID
     */
    @Transactional(readOnly = true)
    public VendorProfileResponseDto getVendorByPublicId(String publicUserId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(publicUserId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Vendor not found with ID: " + publicUserId));

        return vendorMapper.toResponseDto(vendor);
    }

    /**
     * Search for vendors by name or cuisine type
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> searchVendors(String query) {
        List<VendorProfile> vendors = vendorProfileRepository.findByRestaurantNameContainingIgnoreCase(query);

        // Also search by cuisine type and merge results
        List<VendorProfile> byCuisine = vendorProfileRepository.findByCuisineTypeContainingIgnoreCase(query);

        // Merge and remove duplicates
        vendors.addAll(byCuisine);
        return vendors.stream()
                .distinct()
                .filter(VendorProfile::getIsActive)
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get vendors by cuisine type
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVendorsByCuisine(String cuisineType) {
        return vendorProfileRepository.findByCuisineTypeIgnoreCase(cuisineType).stream()
                .filter(VendorProfile::getIsActive)
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get vendors by city
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVendorsByCity(String city) {
        return vendorProfileRepository.findByCity(city).stream()
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get currently open vendors
     * Note: Open status is calculated from operating hours, so filtering is done in-memory
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getOpenVendors() {
        return vendorProfileRepository.findOpenVendors().stream()
                .filter(VendorProfile::isOpenNow)  // Filter in-memory based on current time
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get top-rated vendors
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getTopRatedVendors() {
        return vendorProfileRepository.findTopRatedVendors().stream()
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get verified and active vendors
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVerifiedVendors() {
        return vendorProfileRepository.findByIsVerifiedAndIsActive(true, true).stream()
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get popular cuisines with vendor count, total orders, average rating,
     * sample vendors and products for frontend display
     */
    @Transactional(readOnly = true)
    public List<PopularCuisineDto> getPopularCuisines() {
        // Get all active vendors
        List<VendorProfile> allVendors = vendorProfileRepository.findByIsActive(true);

        // Group by cuisine type and calculate statistics
        Map<String, List<VendorProfile>> cuisineGroups = allVendors.stream()
                .filter(v -> v.getCuisineType() != null && !v.getCuisineType().isBlank())
                .collect(Collectors.groupingBy(VendorProfile::getCuisineType));

        // Build PopularCuisineDto for each cuisine
        return cuisineGroups.entrySet().stream()
                .map(entry -> {
                    String cuisineType = entry.getKey();
                    List<VendorProfile> vendors = entry.getValue();

                    // Calculate total orders across all vendors for this cuisine
                    Long totalOrders = vendors.stream()
                            .mapToLong(VendorProfile::getTotalOrdersCompleted)
                            .sum();

                    // Calculate average rating across all vendors for this cuisine
                    double averageRating = vendors.stream()
                            .mapToDouble(VendorProfile::getAverageRating)
                            .average()
                            .orElse(0.0);

                    // Get top 3 vendors by total orders
                    List<PopularCuisineDto.VendorSummary> sampleVendors = vendors.stream()
                            .sorted(Comparator.comparingInt(VendorProfile::getTotalOrdersCompleted).reversed())
                            .limit(3)
                            .map(v -> PopularCuisineDto.VendorSummary.builder()
                                    .publicVendorId(v.getPublicVendorId())
                                    .restaurantName(v.getRestaurantName())
                                    .logoUrl(v.getLogoUrl())
                                    .rating(v.getAverageRating())
                                    .totalOrders(v.getTotalOrdersCompleted())
                                    .isActive(v.getIsActive())
                                    .build())
                            .toList();

                    // Get sample products from vendors (max 6 products)
                    List<PopularCuisineDto.ProductSummary> sampleProducts = vendors.stream()
                            .flatMap(v -> v.getProducts().stream())
                            .filter(Product::getAvailable)
                            .sorted(Comparator.comparingInt(Product::getTotalOrders).reversed())
                            .limit(6)
                            .map(p -> PopularCuisineDto.ProductSummary.builder()
                                    .publicProductId(p.getPublicProductId())
                                    .productName(p.getName())
                                    .imageUrl(p.getImageUrl())
                                    .price(p.getPrice().doubleValue())
                                    .rating(p.getAverageRating())
                                    .isAvailable(p.getAvailable())
                                    .vendorName(p.getVendor().getRestaurantName())
                                    .build())
                            .toList();

                    // Get representative image (from first vendor with logo or first product with image)
                    String imageUrl = vendors.stream()
                            .map(VendorProfile::getLogoUrl)
                            .filter(url -> url != null && !url.isBlank())
                            .findFirst()
                            .orElse(sampleProducts.stream()
                                    .map(PopularCuisineDto.ProductSummary::getImageUrl)
                                    .filter(url -> url != null && !url.isBlank())
                                    .findFirst()
                                    .orElse(null));

                    return PopularCuisineDto.builder()
                            .cuisineType(cuisineType)
                            .vendorCount((long) vendors.size())
                            .totalOrders(totalOrders)
                            .averageRating(Math.round(averageRating * 10.0) / 10.0) // Round to 1 decimal
                            .sampleVendors(sampleVendors)
                            .sampleProducts(sampleProducts)
                            .imageUrl(imageUrl)
                            .build();
                })
                .sorted(Comparator.comparingLong(PopularCuisineDto::getTotalOrders).reversed())
                .toList();
    }

    // ========== PRODUCT SEARCH ==========

    /**
     * Search for products by name or description
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> searchProducts(String query) {
        return productRepository
                .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query).stream()
                .filter(Product::getAvailable)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Search for available products by name
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> searchProductsByName(String name) {
        return productRepository.findByNameContainingIgnoreCaseAndAvailable(name, true).stream()
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get products by category
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        return productRepository.findByCategoryAndAvailable(category, true).stream()
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get products by price range
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepository.findByPriceBetweenAndAvailable(minPrice, maxPrice, true).stream()
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get vegetarian products
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getVegetarianProducts() {
        return productRepository.findByIsVegetarianAndAvailable(true, true).stream()
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get vegan products
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getVeganProducts() {
        return productRepository.findByIsVeganAndAvailable(true, true).stream()
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get gluten-free products
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getGlutenFreeProducts() {
        return productRepository.findByIsGlutenFreeAndAvailable(true, true).stream()
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get popular products
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getPopularProducts() {
        return productRepository.findPopularProducts().stream()
                .filter(Product::getAvailable)
                .limit(20)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get Chef's Special products from African Kitchen or African Soups categories
     * Returns top products sorted by order count and rating
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getChefSpecials() {
        return productRepository.findChefSpecials().stream()
                .sorted(Comparator.comparing(Product::getAverageRating).reversed()) // Secondary sort by rating
                .limit(10)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get Featured Products - highly ordered products from verified vendors
     * Returns top products sorted by order count and review count
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getFeaturedProducts() {
        return productRepository.findFeaturedProducts().stream()
                .limit(8)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get products from best restaurants near user (by city)
     * Returns products from top-rated restaurants in the specified city
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsFromBestRestaurantsNearMe(String city) {
        return productRepository.findProductsByBestRestaurantsInCity(city).stream()
                .limit(12)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get most popular products in the last 3 months
     * Returns products with the most orders in the last 3 months (better for MVP with limited data)
     * Optionally filtered by city
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getMostPopularProductsThisMonth(String city) {
        // Go back 3 months to get enough data for MVP
        java.time.LocalDateTime threeMonthsAgo = java.time.LocalDateTime.now()
                .minusMonths(3)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        List<Product> products;
        if (city != null && !city.trim().isEmpty()) {
            products = productRepository.findMostPopularProductsThisMonthByCity(threeMonthsAgo, city);
        } else {
            products = productRepository.findMostPopularProductsThisMonth(threeMonthsAgo);
        }

        return products.stream()
                .limit(10)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get top N popular product names only (lightweight for frontend)
     * Returns simple array of unique product names sorted by order count
     * Excludes products from "African Groceries" and "Farm Produce" categories
     */
    @Transactional(readOnly = true)
    public List<String> getPopularProductNames(int limit) {
        return productRepository.findPopularProducts().stream()
                .filter(Product::getAvailable)
                .filter(product -> {
                    String categoryName = product.getCategory().getName();
                    return !categoryName.equalsIgnoreCase("African Groceries")
                            && !categoryName.equalsIgnoreCase("Farm Produce");
                })
                .map(Product::getName)
                .distinct()  // Ensure no duplicate product names
                .limit(limit)
                .toList();
    }

    /**
     * Get top-rated products
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getTopRatedProducts() {
        return productRepository.findTopRatedProducts().stream()
                .limit(20)
                .map(this::toProductResponseDto)
                .toList();
    }

    // ========== CATEGORY SEARCH ==========

    /**
     * Search for categories by name
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> searchCategories(String query) {
        return categoryRepository.findByNameContainingIgnoreCaseAndIsActive(query, true).stream()
                .map(this::toCategoryResponseDto)
                .toList();
    }

    /**
     * Get all active categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getAllActiveCategories() {
        return categoryRepository.findByIsActiveOrderByDisplayOrderAsc(true).stream()
                .map(this::toCategoryResponseDto)
                .toList();
    }

    // ========== ADVANCED FILTERS ==========

    /**
     * Advanced product search with multiple filters including vendor location
     * Supports pagination with page and size parameters
     * Returns PageResponse with metadata (total count, page info, etc.)
     */
    @Transactional(readOnly = true)
    public ApiResponse.PageResponse<ProductResponseDto> advancedProductSearch(
            String query,
            String city,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean isVegetarian,
            Boolean isVegan,
            Boolean isGlutenFree,
            int page,
            int size) {

        List<Product> products;
        if (query != null && !query.isBlank()) {
            products = productRepository
                    .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        } else {
            products = productRepository.findByAvailable(true);
        }

        // Apply all filters and collect to list
        List<ProductResponseDto> filteredProducts = products.stream()
                .filter(Product::getAvailable)
                .filter(p -> city == null || city.isBlank() ||
                        (p.getVendor() != null &&
                         p.getVendor().getAddress().getCity()!= null &&
                         p.getVendor().getAddress().getCity().equalsIgnoreCase(city)))
                .filter(p -> categoryId == null ||
                        (p.getCategory() != null && p.getCategory().getCategoryId().equals(categoryId)))
                .filter(p -> minPrice == null || p.getPrice().compareTo(minPrice) >= 0)
                .filter(p -> maxPrice == null || p.getPrice().compareTo(maxPrice) <= 0)
                .filter(p -> isVegetarian == null || p.getIsVegetarian().equals(isVegetarian))
                .filter(p -> isVegan == null || p.getIsVegan().equals(isVegan))
                .filter(p -> isGlutenFree == null || p.getIsGlutenFree().equals(isGlutenFree))
                .map(this::toProductResponseDto)
                .toList();

        // Calculate pagination metadata
        long totalElements = filteredProducts.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, (int) totalElements);

        // Get current page content
        List<ProductResponseDto> pageContent = filteredProducts.subList(
                Math.min(startIndex, (int) totalElements),
                Math.min(endIndex, (int) totalElements)
        );

        // Build PageResponse
        return ApiResponse.PageResponse.<ProductResponseDto>builder()
                .content(pageContent)
                .pageNumber(page)
                .pageSize(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    /**
     * Advanced vendor search with filters
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> advancedVendorSearch(
            String query,
            String cuisineType,
            String city,
            Boolean isVerified,
            Boolean isOpenNow) {

        List<VendorProfile> vendors;

        if (query != null && !query.isBlank()) {
            vendors = vendorProfileRepository.findByRestaurantNameContainingIgnoreCase(query);
            List<VendorProfile> byCuisine = vendorProfileRepository.findByCuisineTypeContainingIgnoreCase(query);
            vendors.addAll(byCuisine);
            vendors = vendors.stream().distinct().toList();
        } else {
            vendors = vendorProfileRepository.findAll();
        }

        return vendors.stream()
                .filter(VendorProfile::getIsActive)
                .filter(v -> cuisineType == null ||
                        (v.getCuisineType() != null && v.getCuisineType().equalsIgnoreCase(cuisineType)))
                .filter(v -> city == null ||
                        (v.getAddress() != null && v.getAddress().getCity().equalsIgnoreCase(city)))
                .filter(v -> isVerified == null || v.getIsVerified().equals(isVerified))
                .filter(v -> isOpenNow == null || v.isOpenNow() == isOpenNow)
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    // ========== INNER CLASSES FOR SEARCH RESULTS ==========

    @lombok.Data
    @lombok.Builder
    public static class UniversalSearchResults {
        private List<VendorProfileResponseDto> vendors;
        private List<ProductResponseDto> products;
        private List<CategoryResponseDto> categories;
    }

    // ========== MAPPING METHODS ==========

    private ProductResponseDto toProductResponseDto(Product product) {
        return ProductResponseDto.builder()
                .publicProductId(product.getPublicProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .available(product.getAvailable())
                .preparationTimeMinutes(product.getPreparationTimeMinutes())
                .calories(product.getCalories())
                .isVegetarian(product.getIsVegetarian())
                .isVegan(product.getIsVegan())
                .isGlutenFree(product.getIsGlutenFree())
                .isSpicy(product.getIsSpicy())
                .vendorPublicId(product.getVendor() != null ? product.getVendor().getPublicVendorId() : null)
                .restaurantName(product.getVendor() != null ? product.getVendor().getRestaurantName() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                // Vendor Address Information
                .vendorAddressLine(product.getVendor() != null && product.getVendor().getAddress() != null
                        ? product.getVendor().getAddress().getAddressLine() : null)
                .vendorCity(product.getVendor() != null && product.getVendor().getAddress() != null
                        ? product.getVendor().getAddress().getCity() : null)
                .vendorProvince(product.getVendor() != null && product.getVendor().getAddress() != null
                        ? product.getVendor().getAddress().getProvince() : null)
                .vendorPostalCode(product.getVendor() != null && product.getVendor().getAddress() != null
                        ? product.getVendor().getAddress().getPostalCode() : null)
                .vendorCountry(product.getVendor() != null && product.getVendor().getAddress() != null
                        ? product.getVendor().getAddress().getCountry() : null)
                .vendorFormattedAddress(product.getVendor() != null && product.getVendor().getAddress() != null
                        ? product.getVendor().getAddress().getFormattedAddress() : null)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .totalOrders(product.getTotalOrders())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private CategoryResponseDto toCategoryResponseDto(Category category) {
        return CategoryResponseDto.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .description(category.getDescription())
                .iconUrl(category.getIconUrl())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .productCount(category.getProductCount())
                .activeProductCount(category.getActiveProductCount())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}