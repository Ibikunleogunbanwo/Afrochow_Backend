package com.afrochow.search;

import com.afrochow.category.model.Category;
import com.afrochow.common.ApiResponse;
import com.afrochow.common.enums.ScheduleType;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

        private final VendorProfileRepository vendorProfileRepository;
        private final ProductRepository productRepository;
        private final VendorMapper vendorMapper;
        private final VendorGeoIndexService vendorGeoIndexService;

        // ========== VENDOR SEARCH ==========

        /**
         * Get a single vendor by public user ID.
         */
        @Transactional(readOnly = true)
        public VendorProfileResponseDto getVendorByPublicId(String publicUserId, Double lat, Double lng) {
                VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(publicUserId)
                                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + publicUserId));
                VendorProfileResponseDto dto = vendorMapper.toResponseDto(vendor);

                if (lat != null && lng != null) {
                        Map<String, Double> distances = vendorGeoIndexService.getDistancesKm(
                                        lat, lng, List.of(vendor.getPublicVendorId()));
                        dto.setDistanceKm(distances.get(vendor.getPublicVendorId()));
                }

                return dto;
        }

        /**
         * Is this city/location an active Afrochow market — i.e. is there at least
         * one active + verified vendor either in the named city or within a
         * reasonable radius of the given coordinates?
         *
         * Distinct from the homepage rails' own "fall back to nationwide content"
         * behaviour: those fall back whenever the LOCAL pool is too thin to fill a
         * rail (e.g. a brand-new market with 2 vendors), which is a fine, honest
         * thing to paper over with popular-elsewhere content. This check answers a
         * different question — "does Afrochow operate here at all" — so the
         * frontend can show a real "we're not in your area yet" state instead of
         * silently serving another city's vendors as if they were local.
         *
         * Coordinates are checked first (more precise, catches unincorporated/
         * suburb mismatches a raw city-string compare would miss); city name is
         * the fallback when coordinates aren't available yet (e.g. IP-based
         * detection before the browser grants precise geolocation).
         */
        @Transactional(readOnly = true)
        public boolean isMarketServed(String city, Double lat, Double lng) {
                final double MARKET_RADIUS_KM = 100; // generous metro-area radius

                if (lat != null && lng != null) {
                        return !vendorProfileRepository
                                        .findVendorsNearCoordinates(lat, lng, MARKET_RADIUS_KM)
                                        .isEmpty();
                }
                if (city != null && !city.isBlank()) {
                        return !vendorProfileRepository.findByCity(city.trim()).isEmpty();
                }
                // No location signal at all — assume served rather than falsely telling
                // someone we're not in their area before we even know where they are.
                return true;
        }

        /**
         * "Same dish elsewhere" — given a product, finds that exact dish (by name)
         * sold by other active + verified vendors. Backs the restaurant page's
         * "Also available at" section. Location is a hard gate: pass lat/lng to
         * keep only nearby vendors, or city to keep only vendors in that city. If
         * the caller sends no location, fall back to the source product's vendor city
         * so this section never becomes a nationwide product list.
         */
        @Transactional(readOnly = true)
        public List<ProductResponseDto> getSimilarProducts(String publicProductId, String city, Double lat, Double lng) {
                Product product = productRepository.findByPublicProductId(publicProductId)
                                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + publicProductId));

                if (product.getVendor() == null) {
                        return List.of();
                }

                List<Product> matches = productRepository.findSameNameAtOtherVendors(
                                product.getName(), product.getVendor().getId());

                if (lat != null && lng != null && !matches.isEmpty()) {
                        List<VendorProfile> nearbyVendors = vendorGeoIndexService.findNearbyVendors(lat, lng, 25, 100);
                        Set<String> nearbyVendorIds = nearbyVendors.stream()
                                        .map(VendorProfile::getPublicVendorId)
                                        .filter(java.util.Objects::nonNull)
                                        .collect(Collectors.toSet());

                        if (nearbyVendorIds.isEmpty()) {
                                return List.of();
                        }

                        List<Product> localMatches = matches.stream()
                                        .filter(p -> p.getVendor() != null)
                                        .filter(p -> nearbyVendorIds.contains(p.getVendor().getPublicVendorId()))
                                        .toList();

                        List<String> vendorIds = localMatches.stream()
                                        .map(p -> p.getVendor().getPublicVendorId())
                                        .distinct()
                                        .toList();

                        Map<String, Double> distances = vendorGeoIndexService.getDistancesKm(lat, lng, vendorIds);

                        return localMatches.stream()
                                        .limit(8)
                                        .map(p -> toProductResponseDto(p, distances.get(p.getVendor().getPublicVendorId())))
                                        .toList();
                }

                String cityGate = normalizeCity(city);
                if (cityGate == null) {
                        cityGate = product.getVendor().getAddress() != null
                                        ? normalizeCity(product.getVendor().getAddress().getCity())
                                        : null;
                }
                if (cityGate == null) {
                        return List.of();
                }

                final String localCity = cityGate;
                return matches.stream()
                                .filter(p -> vendorCityMatches(p, localCity))
                                .limit(8)
                                .map(this::toProductResponseDto)
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

        // ========== PRODUCT SEARCH ==========

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
        public List<ProductResponseDto> getFeaturedProducts(String city, Double lat, Double lng) {
                return getFeaturedProducts(city, lat, lng, null);
        }

        /**
         * Overload supporting an optional scheduleType filter — lets the homepage split
         * featured products into a "ready to order today" rail (SAME_DAY) and a
         * "pre-order / advance notice" rail (ADVANCE_ORDER) without duplicating the
         * ranking/diversity algorithm. scheduleType == null keeps the original
         * unfiltered behaviour (both schedule types mixed together).
         */
        @Transactional(readOnly = true)
        public List<ProductResponseDto> getFeaturedProducts(String city, Double lat, Double lng, ScheduleType scheduleType) {
                final int POOL_SIZE             = 50;
                final int MAX_PER_VENDOR        = 2;
                final int MAX_PER_CATEGORY      = 2;
                final int MIN_RECENCY_THRESHOLD = 4;

                final String cityFilter = (city != null && !city.isBlank()) ? city.trim() : null;

                // ── Step 1: Admin-pinned products — added as-is, no diversity cap ──
                // Admin explicitly chose these products; diversity caps must not override that.
                // We still track their vendor/category counts so the algorithmic fill (Step 2)
                // diversifies around them.
                List<Product> pinned = productRepository.findAdminFeaturedProducts();
                if (scheduleType != null) {
                        pinned = pinned.stream()
                                        .filter(p -> scheduleType.equals(p.getScheduleType()))
                                        .toList();
                }
                // Admin-pinned products still must not cross city boundaries — otherwise a
                // product pinned in one served market (e.g. Calgary) silently shows up as
                // "Featured Products near you" for a user in a city Afrochow doesn't even
                // operate in yet. Being admin-chosen only exempts a product from the
                // vendor/category diversity caps below, not from location relevance.
                if (cityFilter != null) {
                        pinned = pinned.stream()
                                        .filter(p -> p.getVendor() != null
                                                        && p.getVendor().getAddress() != null
                                                        && cityFilter.equalsIgnoreCase(p.getVendor().getAddress().getCity()))
                                        .toList();
                }

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
                // City filtering happens at the DB query level (not as a Java post-filter
                // over a nationally-ranked pool) so smaller markets aren't starved by
                // vendors from bigger cities crowding out the top-N ranking before the
                // city filter ever gets applied.
                if (result.size() < MAX_TOTAL) {
                        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
                        Pageable pool = PageRequest.of(0, POOL_SIZE);

                        List<Product> candidates = productRepository
                                .findFeaturedProducts(pool, cutoff, cityFilter).getContent();
                        if (scheduleType != null) {
                                candidates = candidates.stream()
                                                .filter(p -> scheduleType.equals(p.getScheduleType()))
                                                .toList();
                        }

                        if (candidates.size() < MIN_RECENCY_THRESHOLD) {
                                candidates = productRepository.findFeaturedProductsBroad(pool, cityFilter).getContent();
                                if (scheduleType != null) {
                                        candidates = candidates.stream()
                                                        .filter(p -> scheduleType.equals(p.getScheduleType()))
                                                        .toList();
                                }
                        }

                        if (candidates.size() < MIN_RECENCY_THRESHOLD) {
                                // Tier 3: sort by rating DESC then newest — better than pure date order
                                candidates = productRepository.findAnyFeaturedProducts(pool, cityFilter).getContent()
                                        .stream()
                                        .filter(p -> scheduleType == null || scheduleType.equals(p.getScheduleType()))
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

                // Distance-from-user — resolved in one Redis round trip (GEORADIUS), keyed
                // by vendor public ID, rather than pulling raw lat/lng out of Postgres and
                // computing distance in application code. Redis is the single source of
                // truth for vendor geo data (kept fresh by VendorGeoIndexService's index).
                Map<String, Double> distancesByVendor = Map.of();
                if (lat != null && lng != null) {
                        List<String> vendorIds = result.stream()
                                .map(Product::getVendor)
                                .filter(java.util.Objects::nonNull)
                                .map(VendorProfile::getPublicVendorId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList();
                        distancesByVendor = vendorGeoIndexService.getDistancesKm(lat, lng, vendorIds);
                }

                final Map<String, Double> distances = distancesByVendor;
                return result.stream()
                        .map(p -> toProductResponseDto(p, p.getVendor() != null
                                ? distances.get(p.getVendor().getPublicVendorId())
                                : null))
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
         * Compatibility endpoint for the frontend's "near me" product rail.
         * City-only callers use the same city-scoped ranking as featured products,
         * which keeps MVP showroom behaviour consistent without requiring browser
         * coordinates.
         */
        @Transactional(readOnly = true)
        public List<ProductResponseDto> getProductsNearMe(String city) {
                return getFeaturedProducts(city, null, null, null);
        }

        /**
         * Products near a coordinate, using the Redis vendor geo index as the primary
         * lookup. Redis gives us eligible vendor IDs ordered/ranked by local relevance;
         * products are then fetched for those vendors and mapped with distance labels.
         * If Redis is unavailable or empty, fall back to the existing DB Haversine query.
         */
        @Transactional(readOnly = true)
        public List<ProductResponseDto> getProductsNearCoordinates(double lat, double lng, double radiusKm) {
                final int vendorLimit = 30;
                final int productLimit = 48;

                List<VendorProfile> nearbyVendors = vendorGeoIndexService.findNearbyVendors(
                                lat, lng, radiusKm, vendorLimit);

                if (!nearbyVendors.isEmpty()) {
                        List<String> vendorIds = nearbyVendors.stream()
                                        .map(VendorProfile::getPublicVendorId)
                                        .filter(java.util.Objects::nonNull)
                                        .distinct()
                                        .toList();

                        List<Product> products = productRepository
                                        .findAvailablePublicProductsByVendorPublicIds(vendorIds);

                        Map<String, Integer> vendorRank = new HashMap<>();
                        for (int i = 0; i < vendorIds.size(); i++) {
                                vendorRank.put(vendorIds.get(i), i);
                        }

                        Map<String, Double> distancesByVendor =
                                        vendorGeoIndexService.getDistancesKm(lat, lng, vendorIds);

                        return products.stream()
                                        .sorted(Comparator
                                                        .comparingInt((Product p) -> vendorRank.getOrDefault(
                                                                        p.getVendor() != null
                                                                                        ? p.getVendor().getPublicVendorId()
                                                                                        : null,
                                                                        Integer.MAX_VALUE))
                                                        .thenComparing(Product::getTotalOrders, Comparator.reverseOrder())
                                                        .thenComparing(Product::getAverageRating, Comparator.reverseOrder()))
                                        .limit(productLimit)
                                        .map(p -> toProductResponseDto(p, p.getVendor() != null
                                                        ? distancesByVendor.get(p.getVendor().getPublicVendorId())
                                                        : null))
                                        .toList();
                }

                return productRepository.findProductsNearCoordinates(lat, lng, radiusKm).stream()
                                .limit(productLimit)
                                .map(this::toProductResponseDto)
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
                        String storeCategory,
                        String city,
                        Boolean isVerified,
                        Boolean isOpenNow) {

                List<VendorProfile> vendors;

                if (query != null && !query.isBlank()) {
                        vendors = new ArrayList<>(
                                        vendorProfileRepository.findByRestaurantNameContainingIgnoreCase(query));
                        vendors.addAll(vendorProfileRepository.findByStoreCategoryContainingIgnoreCase(query));
                        vendors = vendors.stream().distinct().collect(Collectors.toList());
                } else {
                        vendors = new ArrayList<>(vendorProfileRepository.findByIsVerifiedAndIsActive(true, true));
                }

                return vendors.stream()
                                .filter(VendorProfile::getIsActive)
                                .filter(v -> storeCategory == null
                                                || (v.getStoreCategory() != null
                                                                && v.getStoreCategory().equalsIgnoreCase(storeCategory)))
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
         * Get verified+active vendors near GPS coordinates ordered by distance
         * ascending.
         * Used by the "Popular Stores Near You" homepage section.
         */
        @Transactional(readOnly = true)
        public List<VendorProfileResponseDto> getVendorsNearCoordinates(double lat, double lng, double radiusKm) {
                // Primary path — Redis geo index. Each vendor's own maxDeliveryDistanceKm
                // (when delivery-enabled and set) overrides the flat radiusKm; see
                // VendorGeoIndexService.findNearbyVendors.
                List<VendorProfile> redisVendors = vendorGeoIndexService.findNearbyVendors(lat, lng, radiusKm, 12);
                if (!redisVendors.isEmpty()) {
                        return redisVendors.stream()
                                .map(vendorMapper::toResponseDto)
                                .toList();
                }

                // Fallback path — only used when Redis is unavailable/disabled or the index
                // is empty. This JPQL query applies the flat radiusKm to every vendor; it
                // does NOT honor per-vendor maxDeliveryDistanceKm the way the Redis path
                // does. Acceptable as a degraded-mode fallback, not the common case.
                return vendorProfileRepository.findVendorsNearCoordinates(lat, lng, radiusKm).stream()
                                .limit(12)
                                .map(vendorMapper::toResponseDto)
                                .toList();
        }

        // ========== MAPPING ==========

        private ProductResponseDto toProductResponseDto(Product product) {
                return toProductResponseDto(product, null);
        }

        private boolean vendorCityMatches(Product product, String city) {
                if (product.getVendor() == null || product.getVendor().getAddress() == null) {
                        return false;
                }
                return city.equals(normalizeCity(product.getVendor().getAddress().getCity()));
        }

        private String normalizeCity(String city) {
                return city == null || city.isBlank() ? null : city.trim().toLowerCase();
        }

        private ProductResponseDto toProductResponseDto(Product product, Double distanceKm) {
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
                                .distanceKm(distanceKm)
                                .averageRating(product.getAverageRating())
                                .reviewCount(product.getReviewCount())
                                .totalOrders(product.getTotalOrders())
                                .createdAt(product.getCreatedAt())
                                .updatedAt(product.getUpdatedAt())
                                .build();
        }

}
