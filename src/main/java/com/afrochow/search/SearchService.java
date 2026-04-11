package com.afrochow.search;

import com.afrochow.category.dto.CategoryResponseDto;
import com.afrochow.category.model.Category;
import com.afrochow.category.repository.CategoryRepository;
import com.afrochow.common.ApiResponse;
import com.afrochow.common.enums.ScheduleType;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.search.dto.PopularCuisineDto;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.VendorMapper;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
         * Universal search across vendors, products, and categories.
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
         * Get a single vendor by public user ID.
         */
        @Transactional(readOnly = true)
        public VendorProfileResponseDto getVendorByPublicId(String publicUserId) {
                VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(publicUserId)
                                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + publicUserId));
                return vendorMapper.toResponseDto(vendor);
        }

        /**
         * Search vendors by name or cuisine type.
         * Only returns active and verified vendors.
         */
        @Transactional(readOnly = true)
        public List<VendorProfileResponseDto> searchVendors(String query) {
                List<VendorProfile> vendors = new ArrayList<>(
                                vendorProfileRepository.findByRestaurantNameContainingIgnoreCase(query));
                vendors.addAll(vendorProfileRepository.findByCuisineTypeContainingIgnoreCase(query));

                return vendors.stream()
                                .distinct()
                                .filter(v -> v.getIsActive() && v.getIsVerified())
                                .map(vendorMapper::toResponseDto)
                                .toList();
        }

        /**
         * Find vendors that carry at least one available product matching the query.
         * Only returns active and verified vendors.
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
         * isOpenNow() is @Transient so must be evaluated in-memory after DB fetch.
         * Repository query already filters by isActive and isVerified.
         */
        @Transactional(readOnly = true)
        public List<VendorProfileResponseDto> getOpenVendors() {
                return vendorProfileRepository.findActiveAndVerifiedVendors().stream()
                                .filter(VendorProfile::isOpenNow)
                                .map(vendorMapper::toResponseDto)
                                .toList();
        }

        /**
         * Get top-rated vendors (minimum 5 visible reviews).
         * Repository query already filters by isActive and isVerified.
         */
        @Transactional(readOnly = true)
        public List<VendorProfileResponseDto> getTopRatedVendors() {
                return vendorProfileRepository.findTopRatedVendors(5, PageRequest.of(0, 30)).stream()
                                .map(vendorMapper::toResponseDto)
                                .toList();
        }

        /**
         * Get all verified and active vendors.
         */
        @Transactional(readOnly = true)
        public List<VendorProfileResponseDto> getVerifiedVendors() {
                return vendorProfileRepository.findByIsVerifiedAndIsActive(true, true).stream()
                                .map(vendorMapper::toResponseDto)
                                .toList();
        }

        /**
         * Get popular cuisines with vendor count, total orders, average rating,
         * and sample vendors/products for frontend display.
         * Uses JOIN FETCH to load vendors + products in one query (avoids N+1).
         */
        @Transactional(readOnly = true)
        public List<PopularCuisineDto> getPopularCuisines() {
                List<VendorProfile> allVendors = vendorProfileRepository.findByIsActiveWithProducts(true);

                Map<String, List<VendorProfile>> cuisineGroups = allVendors.stream()
                                .filter(v -> v.getCuisineType() != null && !v.getCuisineType().isBlank())
                                .collect(Collectors.groupingBy(VendorProfile::getCuisineType));

                return cuisineGroups.entrySet().stream()
                                .map(entry -> {
                                        String cuisineType = entry.getKey();
                                        List<VendorProfile> vendors = entry.getValue();

                                        long totalOrders = vendors.stream()
                                                        .mapToLong(VendorProfile::getTotalOrdersCompleted)
                                                        .sum();

                                        double averageRating = vendors.stream()
                                                        .mapToDouble(VendorProfile::getAverageRating)
                                                        .average()
                                                        .orElse(0.0);

                                        List<PopularCuisineDto.VendorSummary> sampleVendors = vendors.stream()
                                                        .sorted(Comparator.comparingInt(
                                                                        VendorProfile::getTotalOrdersCompleted)
                                                                        .reversed())
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
                                                        .sorted(Comparator.comparingInt(Product::getTotalOrders)
                                                                        .reversed())
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
                                                        .orElseGet(() -> sampleProducts.stream()
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
         * Search products by name or description.
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
         * Search available products by name only.
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
         * Get available products by category.
         */
        @Transactional(readOnly = true)
        public List<ProductResponseDto> getProductsByCategory(Long categoryId) {
                Category category = categoryRepository.findById(categoryId)
                                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

                return productRepository.findByCategoryAndAvailable(category, true).stream()
                                .filter(p -> p.getVendor() != null
                                                && p.getVendor().getIsVerified()
                                                && p.getVendor().getIsActive())
                                .map(this::toProductResponseDto)
                                .toList();
        }

        /**
         * Get available products within a price range.
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
         * Get available vegetarian products.
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
         * Get available vegan products.
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
         * Get available gluten-free products.
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
         * Get popular products (top 20).
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
         * Get Featured Products — admin-pinned first, then trending, spread across vendors.
         *
         * Strategy:
         * 1. Always include admin-pinned products (isFeatured = true) up to MAX_TOTAL.
         * 2. Fill remaining slots with algorithmically ranked products:
         *    Tier 1 — orders in last 90 days (recency trending)
         *    Tier 2 — all-time order ranking (broader history)
         *    Tier 3 — best rated then newest (zero-order fallback for new platforms)
         * 3. Tier 3 applies category diversity (max 2 per category) and sorts by
         *    average rating DESC so quality products surface over pure recency.
         * 4. Apply vendor diversity: at most MAX_PER_VENDOR per vendor across all slots.
         */
        @Transactional(readOnly = true)
        public List<ProductResponseDto> getFeaturedProducts() {
                final int POOL_SIZE             = 50;
                final int MAX_PER_VENDOR        = 2;
                final int MAX_PER_CATEGORY      = 2;
                final int MIN_RECENCY_THRESHOLD = 4;

                // ── Step 1: Admin-pinned products — added as-is, no diversity cap ──
                // Admin explicitly chose these products; diversity caps must not override that.
                // We still track their vendor/category counts so the algorithmic fill (Step 2)
                // diversifies around them.
                List<Product> pinned = productRepository.findAdminFeaturedProducts();

                // All pinned products always show + up to 8 algorithmic fill slots
                final int MAX_TOTAL = Math.max(24, pinned.size() + 8);
                Set<Long> pinnedIds  = pinned.stream()
                        .map(Product::getProductId)
                        .collect(java.util.stream.Collectors.toSet());

                Map<String, Integer> vendorCount    = new HashMap<>();
                Map<Long,   Integer> categoryCount  = new HashMap<>();
                List<Product>        result          = new ArrayList<>();

                for (Product p : pinned) {
                        if (p.getVendor() == null) continue;
                        result.add(p);
                        // Track counts for Step 2 diversity only
                        String vendorId   = p.getVendor().getPublicVendorId();
                        Long   categoryId = p.getCategory() != null ? p.getCategory().getCategoryId() : null;
                        vendorCount.merge(vendorId, 1, Integer::sum);
                        if (categoryId != null) categoryCount.merge(categoryId, 1, Integer::sum);
                }

                // ── Step 2: Fill remaining slots algorithmically ──────────────────
                if (result.size() < MAX_TOTAL) {
                        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
                        Pageable pool = PageRequest.of(0, POOL_SIZE);

                        List<Product> candidates = productRepository
                                .findFeaturedProducts(pool, cutoff).getContent();

                        if (candidates.size() < MIN_RECENCY_THRESHOLD) {
                                candidates = productRepository.findFeaturedProductsBroad(pool).getContent();
                        }

                        if (candidates.size() < MIN_RECENCY_THRESHOLD) {
                                // Tier 3: sort by rating DESC then newest — better than pure date order
                                candidates = productRepository.findAnyFeaturedProducts(pool).getContent()
                                        .stream()
                                        .sorted(Comparator
                                                .comparingDouble(Product::getAverageRating).reversed()
                                                .thenComparing(Product::getReviewCount,   Comparator.reverseOrder())
                                                .thenComparing(Product::getCreatedAt,     Comparator.reverseOrder()))
                                        .toList();
                        }

                        for (Product p : candidates) {
                                if (p.getVendor() == null) continue;
                                if (pinnedIds.contains(p.getProductId())) continue; // already in result
                                applyDiversity(p, result, vendorCount, categoryCount, MAX_PER_VENDOR, MAX_PER_CATEGORY, MAX_TOTAL);
                                if (result.size() >= MAX_TOTAL) break;
                        }
                }

                return result.stream()
                        .map(this::toProductResponseDto)
                        .toList();
        }

        /** Adds a product to the result list if vendor + category diversity caps allow it. */
        private void applyDiversity(
                Product p,
                List<Product> result,
                Map<String, Integer> vendorCount,
                Map<Long,   Integer> categoryCount,
                int maxPerVendor,
                int maxPerCategory,
                int maxTotal
        ) {
                if (result.size() >= maxTotal) return;

                String vendorId   = p.getVendor().getPublicVendorId();
                Long   categoryId = p.getCategory() != null ? p.getCategory().getCategoryId() : null;

                int vc = vendorCount.getOrDefault(vendorId, 0);
                if (vc >= maxPerVendor) return;

                if (categoryId != null) {
                        int cc = categoryCount.getOrDefault(categoryId, 0);
                        if (cc >= maxPerCategory) return;
                        categoryCount.put(categoryId, cc + 1);
                }

                vendorCount.put(vendorId, vc + 1);
                result.add(p);
        }

        /**
         * Get products from the best restaurants in a given city.
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
                LocalDateTime threeMonthsAgo = LocalDateTime.now()
                                .minusMonths(3)
                                .withDayOfMonth(1)
                                .withHour(0)
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0);

                List<Product> products = (city != null && !city.isBlank())
                                ? productRepository.findMostPopularProductsThisMonthByCity(threeMonthsAgo, city)
                                : productRepository.findMostPopularProductsThisMonth(threeMonthsAgo);

                return products.stream()
                                .limit(10)
                                .map(this::toProductResponseDto)
                                .toList();
        }

        /**
         * Get top N popular product names (lightweight for frontend typeahead/suggestions).
         * Excludes African Groceries and Farm Produce categories.
         * Repository query already filters by isActive and isVerified.
         */
        @Transactional(readOnly = true)
        public List<String> getPopularProductNames(int limit) {
                return productRepository.findPopularProducts().stream()
                                .filter(Product::getAvailable)
                                .filter(p -> {
                                        // category_id is nullable (ON DELETE SET NULL)
                                        if (p.getCategory() == null)
                                                return true;
                                        String name = p.getCategory().getName();
                                        return !name.equalsIgnoreCase("African Groceries")
                                                        && !name.equalsIgnoreCase("Farm Produce");
                                })
                                .map(Product::getName)
                                .distinct()
                                .limit(limit)
                                .toList();
        }

        /**
         * Get top-rated products (top 20).
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
         * Search active categories by name.
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
         * Advanced paginated product search with multiple optional filters.
         * Only returns products from active and verified vendors.
         * scheduleType is applied as a Java stream filter post-query — Hibernate 6
         * cannot reliably evaluate :enumParam IS NULL for typed enum params in JPQL.
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
                        ScheduleType scheduleType,
                        int page,
                        int size) {

                String nameParam = (query != null && !query.isBlank()) ? query.trim() : null;
                String cityParam = (city != null && !city.isBlank()) ? city.trim() : null;

                List<Product> matched = productRepository.findByFilters(
                                nameParam, cityParam, categoryId, minPrice, maxPrice,
                                isVegetarian, isVegan, isGlutenFree);

                // Apply scheduleType in Java — see note above
                List<Product> filtered = (scheduleType == null)
                                ? matched
                                : matched.stream()
                                                .filter(p -> scheduleType.equals(p.getScheduleType()))
                                                .toList();

                long totalElements = filtered.size();
                int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
                int startIndex = Math.min(page * size, (int) totalElements);
                int endIndex = Math.min(startIndex + size, (int) totalElements);

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
         * Advanced vendor search with optional filters.
         * When no query is provided, scopes to active+verified at DB level.
         * isOpenNow is @Transient and must be evaluated in-memory.
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
                        vendors = new ArrayList<>(
                                        vendorProfileRepository.findByRestaurantNameContainingIgnoreCase(query));
                        vendors.addAll(vendorProfileRepository.findByCuisineTypeContainingIgnoreCase(query));
                        vendors = vendors.stream().distinct().collect(Collectors.toList());
                } else {
                        vendors = new ArrayList<>(vendorProfileRepository.findByIsVerifiedAndIsActive(true, true));
                }

                return vendors.stream()
                                .filter(VendorProfile::getIsActive)
                                .filter(v -> cuisineType == null
                                                || (v.getCuisineType() != null
                                                                && v.getCuisineType().equalsIgnoreCase(cuisineType)))
                                .filter(v -> city == null
                                                || (v.getAddress() != null
                                                                && v.getAddress().getCity().equalsIgnoreCase(city)))
                                .filter(v -> isVerified == null || v.getIsVerified().equals(isVerified))
                                // isOpenNow is @Transient — must stay in-memory
                                .filter(v -> isOpenNow == null || v.isOpenNow() == isOpenNow)
                                .map(vendorMapper::toResponseDto)
                                .toList();
        }

        /**
         * Get products near GPS coordinates within a radius.
         */
        @Transactional(readOnly = true)
        public List<ProductResponseDto> getProductsNearCoordinates(double lat, double lng, double radiusKm) {
                return productRepository.findProductsNearCoordinates(lat, lng, radiusKm).stream()
                                .limit(12)
                                .map(this::toProductResponseDto)
                                .toList();
        }

        /**
         * Get verified+active vendors near GPS coordinates ordered by distance
         * ascending.
         * Used by the "Popular Stores Near You" homepage section.
         */
        @Transactional(readOnly = true)
        public List<VendorProfileResponseDto> getVendorsNearCoordinates(double lat, double lng, double radiusKm) {
                return vendorProfileRepository.findVendorsNearCoordinates(lat, lng, radiusKm).stream()
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

        // ========== MAPPING ==========

        private ProductResponseDto toProductResponseDto(Product product) {
                VendorProfile vendor   = product.getVendor();
                Category category = product.getCategory();

                // Safely extract vendor address fields
                var address = (vendor != null) ? vendor.getAddress() : null;

                return ProductResponseDto.builder()
                                .publicProductId(product.getPublicProductId())
                                .name(product.getName())
                                .description(product.getDescription())
                                .price(product.getPrice())
                                .imageUrl(product.getImageUrl())
                                .available(product.getAvailable())
                                .preparationTimeMinutes(product.getPreparationTimeMinutes())
                                .scheduleType(product.getScheduleType())
                                .advanceNoticeHours(product.getAdvanceNoticeHours())
                                .calories(product.getCalories())
                                .isVegetarian(product.getIsVegetarian())
                                .isVegan(product.getIsVegan())
                                .isGlutenFree(product.getIsGlutenFree())
                                .isSpicy(product.getIsSpicy())
                                .isFeatured(product.getIsFeatured())
                                .featuredAt(product.getFeaturedAt())
                                .vendorPublicId(vendor != null ? vendor.getPublicVendorId() : null)
                                .restaurantName(vendor != null ? vendor.getRestaurantName() : null)
                                .categoryId(category != null ? category.getCategoryId() : null)
                                .categoryName(category != null ? category.getName() : null)
                                .vendorAddressLine(address != null ? address.getAddressLine() : null)
                                .vendorCity(address != null ? address.getCity() : null)
                                .vendorProvince(address != null ? address.getProvince() : null)
                                .vendorPostalCode(address != null ? address.getPostalCode() : null)
                                .vendorCountry(address != null ? address.getCountry() : null)
                                .vendorFormattedAddress(address != null ? address.getFormattedAddress() : null)
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