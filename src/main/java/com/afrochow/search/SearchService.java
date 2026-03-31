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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Vendor not found with ID: " + publicUserId));
        return vendorMapper.toResponseDto(vendor);
    }

    /**
     * Search for vendors by name or cuisine type.
     * Only returns active and verified vendors.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> searchVendors(String query) {
        List<VendorProfile> vendors = vendorProfileRepository.findByRestaurantNameContainingIgnoreCase(query);
        List<VendorProfile> byCuisine = vendorProfileRepository.findByCuisineTypeContainingIgnoreCase(query);

        vendors.addAll(byCuisine);
        return vendors.stream()
                .distinct()
                .filter(v -> v.getIsActive() && v.getIsVerified())
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Find vendors by product name — returns distinct verified+active vendors
     * that carry at least one available product matching the query.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVendorsByProductName(String query) {
        return productRepository
                .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query)
                .stream()
                .filter(p -> p.getAvailable()
                        && p.getVendor() != null
                        && p.getVendor().getIsActive()
                        && p.getVendor().getIsVerified())
                .map(Product::getVendor)
                .distinct()
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get vendors by cuisine type.
     * Only returns active and verified vendors.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVendorsByCuisine(String cuisineType) {
        return vendorProfileRepository.findByCuisineTypeIgnoreCase(cuisineType).stream()
                .filter(v -> v.getIsActive() && v.getIsVerified())
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get vendors by city.
     * Repository query already filters by isActive and isVerified.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVendorsByCity(String city) {
        return vendorProfileRepository.findByCity(city).stream()
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get currently open vendors.
     * isOpenNow() is @Transient — filtering must be done in-memory after DB fetch.
     * Repository query already filters by isActive and isVerified.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getOpenVendors() {
        return vendorProfileRepository.findOpenVendors().stream()
                .filter(VendorProfile::isOpenNow)
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get top-rated vendors.
     * Repository query already filters by isActive and isVerified,
     * and requires a minimum of 5 visible reviews.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getTopRatedVendors() {
        return vendorProfileRepository.findTopRatedVendors(5, PageRequest.of(0, 30)).stream()
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get verified and active vendors.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVerifiedVendors() {
        return vendorProfileRepository.findByIsVerifiedAndIsActive(true, true).stream()
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    /**
     * Get popular cuisines with vendor count, total orders, average rating,
     * sample vendors and products for frontend display.
     * Only includes active vendors.
     */
    @Transactional(readOnly = true)
    public List<PopularCuisineDto> getPopularCuisines() {
        List<VendorProfile> allVendors = vendorProfileRepository.findByIsActive(true);

        Map<String, List<VendorProfile>> cuisineGroups = allVendors.stream()
                .filter(v -> v.getCuisineType() != null && !v.getCuisineType().isBlank())
                .collect(Collectors.groupingBy(VendorProfile::getCuisineType));

        return cuisineGroups.entrySet().stream()
                .map(entry -> {
                    String cuisineType = entry.getKey();
                    List<VendorProfile> vendors = entry.getValue();

                    Long totalOrders = vendors.stream()
                            .mapToLong(VendorProfile::getTotalOrdersCompleted)
                            .sum();

                    double averageRating = vendors.stream()
                            .mapToDouble(VendorProfile::getAverageRating)
                            .average()
                            .orElse(0.0);

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
                            .averageRating(Math.round(averageRating * 10.0) / 10.0)
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
     * Search for products by name or description.
     * Only returns available products from active and verified vendors.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> searchProducts(String query) {
        return productRepository
                .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query).stream()
                .filter(Product::getAvailable)
                .filter(p -> p.getVendor() != null
                        && p.getVendor().getIsVerified()
                        && p.getVendor().getIsActive())
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Search for available products by name.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> searchProductsByName(String name) {
        return productRepository.findByNameContainingIgnoreCaseAndAvailable(name, true).stream()
                .filter(p -> p.getVendor() != null
                        && p.getVendor().getIsVerified()
                        && p.getVendor().getIsActive())
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get products by category.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        return productRepository.findByCategoryAndAvailable(category, true).stream()
                .filter(p -> p.getVendor() != null
                        && p.getVendor().getIsVerified()
                        && p.getVendor().getIsActive())
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get products by price range.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepository.findByPriceBetweenAndAvailable(minPrice, maxPrice, true).stream()
                .filter(p -> p.getVendor() != null
                        && p.getVendor().getIsVerified()
                        && p.getVendor().getIsActive())
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get vegetarian products.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getVegetarianProducts() {
        return productRepository.findByIsVegetarianAndAvailable(true, true).stream()
                .filter(p -> p.getVendor() != null
                        && p.getVendor().getIsVerified()
                        && p.getVendor().getIsActive())
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get vegan products.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getVeganProducts() {
        return productRepository.findByIsVeganAndAvailable(true, true).stream()
                .filter(p -> p.getVendor() != null
                        && p.getVendor().getIsVerified()
                        && p.getVendor().getIsActive())
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get gluten-free products.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getGlutenFreeProducts() {
        return productRepository.findByIsGlutenFreeAndAvailable(true, true).stream()
                .filter(p -> p.getVendor() != null
                        && p.getVendor().getIsVerified()
                        && p.getVendor().getIsActive())
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get popular products.
     * Repository query already filters by isActive and isVerified.
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
     * Get Chef's Special products from African Kitchen or African Soups categories.
     * Repository query already filters by isActive and isVerified.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getChefSpecials() {
        return productRepository.findChefSpecials().stream()
                .sorted(Comparator.comparing(Product::getAverageRating).reversed())
                .limit(10)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get Featured Products — products trending in the last 90 days, spread across vendors.
     *
     * Strategy:
     *  1. Fetch up to 32 products that had at least one order in the last 90 days,
     *     ranked by recent-order count then review count (SQL LIMIT via Pageable).
     *  2. If fewer than 4 results (platform is new / low traffic), fall back to the
     *     all-time broad query so the section is never empty.
     *  3. Apply vendor diversity: at most 2 products per vendor, pick the top 8.
     *     This prevents a single popular vendor from monopolising all 8 cards.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getFeaturedProducts() {
        final int POOL_SIZE    = 50;
        final int MAX_TOTAL    = 16;
        final int MAX_PER_VENDOR = 2;
        final int MIN_RECENCY_THRESHOLD = 4;

        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        Pageable pool = PageRequest.of(0, POOL_SIZE);

        List<Product> candidates = productRepository
                .findFeaturedProducts(pool, cutoff)
                .getContent();

        // Fallback 1: not enough orders in the last 90 days — use all-time order ranking
        if (candidates.size() < MIN_RECENCY_THRESHOLD) {
            candidates = productRepository
                    .findFeaturedProductsBroad(pool)
                    .getContent();
        }

        // Fallback 2: platform has no orders at all yet — show newest available products
        if (candidates.size() < MIN_RECENCY_THRESHOLD) {
            candidates = productRepository
                    .findAnyFeaturedProducts(pool)
                    .getContent();
        }

        // Vendor diversity: at most MAX_PER_VENDOR products per vendor, up to MAX_TOTAL
        Map<String, Integer> vendorCount = new HashMap<>();
        List<Product> diverse = new ArrayList<>();

        for (Product p : candidates) {
            if (p.getVendor() == null) continue;
            String vid = p.getVendor().getPublicVendorId();
            int count = vendorCount.getOrDefault(vid, 0);
            if (count < MAX_PER_VENDOR) {
                diverse.add(p);
                vendorCount.put(vid, count + 1);
            }
            if (diverse.size() >= MAX_TOTAL) break;
        }

        return diverse.stream()
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get products from best restaurants near user (by city).
     * Repository query already filters by isActive and isVerified.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsFromBestRestaurantsNearMe(String city) {
        return productRepository.findProductsByBestRestaurantsInCity(city).stream()
                .limit(12)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get most popular products in the last 3 months, optionally filtered by city.
     * Repository query already filters by isActive and isVerified.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getMostPopularProductsThisMonth(String city) {
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
     * Get top N popular product names only (lightweight for frontend).
     * Excludes African Groceries and Farm Produce categories.
     * Repository query already filters by isActive and isVerified.
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
                .distinct()
                .limit(limit)
                .toList();
    }

    /**
     * Get top-rated products.
     * Repository query already filters by isActive and isVerified.
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
     * Search for categories by name.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> searchCategories(String query) {
        return categoryRepository.findByNameContainingIgnoreCaseAndIsActive(query, true).stream()
                .map(this::toCategoryResponseDto)
                .toList();
    }

    /**
     * Get all active categories ordered by display order.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getAllActiveCategories() {
        return categoryRepository.findByIsActiveOrderByDisplayOrderAsc(true).stream()
                .map(this::toCategoryResponseDto)
                .toList();
    }

    // ========== ADVANCED FILTERS ==========

    /**
     * Advanced product search with multiple filters including vendor location.
     * Only returns products from active and verified vendors.
     * Supports pagination with page and size parameters.
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
            com.afrochow.common.enums.ScheduleType scheduleType,
            int page,
            int size) {

        // Normalize nulls so JPQL :param IS NULL checks work correctly
        String  nameParam  = (query != null && !query.isBlank())  ? query.trim()  : null;
        String  cityParam  = (city  != null && !city.isBlank())   ? city.trim()   : null;

        List<Product> matched = productRepository.findByFilters(
                nameParam,
                cityParam,
                categoryId,
                minPrice,
                maxPrice,
                isVegetarian,
                isVegan,
                isGlutenFree
        );

        // Apply scheduleType filter in Java — Hibernate 6 cannot reliably evaluate
        // `:enumParam IS NULL` for typed enum parameters in JPQL
        List<Product> filtered = scheduleType == null
                ? matched
                : matched.stream()
                         .filter(p -> scheduleType.equals(p.getScheduleType()))
                         .toList();

        long totalElements = filtered.size();
        int  totalPages    = (int) Math.ceil((double) totalElements / size);
        int  startIndex    = Math.min(page * size, (int) totalElements);
        int  endIndex      = Math.min(startIndex + size, (int) totalElements);

        List<ProductResponseDto> pageContent = filtered
                .subList(startIndex, endIndex)
                .stream()
                .map(this::toProductResponseDto)
                .toList();

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
     * Advanced vendor search with filters.
     * Always filters by isActive; respects isVerified param if provided.
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

    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsNearCoordinates(double lat, double lng, double radiusKm) {
        return productRepository.findProductsNearCoordinates(lat, lng, radiusKm)
                .stream()
                .limit(12)
                .map(this::toProductResponseDto)
                .toList();
    }

    /**
     * Get unique verified+active vendors within radiusKm of the given GPS coordinates,
     * ordered by distance ascending (closest first). Used by the "Popular Stores Near You" homepage section.
     */
    @Transactional(readOnly = true)
    public List<VendorProfileResponseDto> getVendorsNearCoordinates(double lat, double lng, double radiusKm) {
        return vendorProfileRepository.findVendorsNearCoordinates(lat, lng, radiusKm)
                .stream()
                .limit(12)
                .map(vendorMapper::toResponseDto)
                .toList();
    }

    // ========== INNER CLASSES ==========

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