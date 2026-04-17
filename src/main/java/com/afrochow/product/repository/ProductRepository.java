package com.afrochow.product.repository;

import com.afrochow.product.model.Product;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.category.model.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ========== FIND BY ID ==========

    Optional<Product> findByPublicProductId(String publicProductId);

    /**
     * Fetch a product by its public ID with a database-level write lock.
     * Use this in toggleProductAvailability() so that two concurrent toggle
     * calls on the same product are serialised — preventing both from reading
     * the same value and both flipping it the same direction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.publicProductId = :publicProductId")
    Optional<Product> findByPublicProductIdWithLock(@Param("publicProductId") String publicProductId);

    // ========== FIND BY VENDOR ==========

    /** Admin / vendor-authenticated use — returns ALL of a vendor's products regardless of suspension. */
    List<Product> findByVendor(VendorProfile vendor);

    /** Admin / vendor-authenticated use — returns available products ignoring adminVisible. */
    List<Product> findByVendorAndAvailable(VendorProfile vendor, Boolean available);

    /**
     * Public use — available products from this vendor that are also platform-visible.
     * Respects admin suspension so customers never see suspended products.
     */
    List<Product> findByVendorAndAvailableTrueAndAdminVisibleTrue(VendorProfile vendor);

    /**
     * Public use — ALL (available or not) products from this vendor that are platform-visible.
     * Used when availableOnly=false on the public vendor products endpoint.
     */
    List<Product> findByVendorAndAdminVisibleTrue(VendorProfile vendor);

    // ========== FIND BY CATEGORY ==========

    /** Admin use — all products in a category regardless of suspension. */
    List<Product> findByCategory(Category category);

    /** Admin use — available products in a category ignoring adminVisible. */
    List<Product> findByCategoryAndAvailable(Category category, Boolean available);

    /**
     * Public use — available, platform-visible products in a category.
     */
    List<Product> findByCategoryAndAvailableTrueAndAdminVisibleTrue(Category category);

    /**
     * Public use — all platform-visible products in a category (available or not).
     */
    List<Product> findByCategoryAndAdminVisibleTrue(Category category);

    // ========== SEARCH BY NAME / DESCRIPTION ==========

    List<Product> findByNameContainingIgnoreCaseAndAvailable(String name, Boolean available);

    /**
     * Public search — matches name or description but only returns platform-visible products.
     */
    @Query("""
        SELECT p FROM Product p
        WHERE p.adminVisible = true
          AND (LOWER(p.name)        LIKE LOWER(CONCAT('%', :query, '%'))
           OR  LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    List<Product> searchPublic(@Param("query") String query);

    // ========== FIND BY AVAILABILITY ==========

    List<Product> findByAvailable(Boolean available);

    /** Public use — available AND platform-visible products. */
    List<Product> findByAvailableTrueAndAdminVisibleTrue();

    // ========== FIND BY PRICE RANGE ==========

    List<Product> findByPriceBetweenAndAvailable(BigDecimal minPrice, BigDecimal maxPrice, Boolean available);

    /** Public use — available, platform-visible products within a price range. */
    List<Product> findByPriceBetweenAndAvailableTrueAndAdminVisibleTrue(BigDecimal minPrice, BigDecimal maxPrice);

    // ========== FIND BY DIETARY ==========

    List<Product> findByIsVegetarianAndAvailable(Boolean isVegetarian, Boolean available);

    List<Product> findByIsVeganAndAvailable(Boolean isVegan, Boolean available);

    List<Product> findByIsGlutenFreeAndAvailable(Boolean isGlutenFree, Boolean available);

    /** Public dietary filters — platform-visible only. */
    List<Product> findByIsVegetarianTrueAndAvailableTrueAndAdminVisibleTrue();

    List<Product> findByIsVeganTrueAndAvailableTrueAndAdminVisibleTrue();

    List<Product> findByIsGlutenFreeTrueAndAvailableTrueAndAdminVisibleTrue();

    // ========== POPULAR PRODUCTS ==========

    /**
     * Products sorted by order count from active and verified vendors only.
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.adminVisible = true AND p.vendor.isVerified = true AND p.vendor.isActive = true ORDER BY SIZE(p.orderLines) DESC")
    List<Product> findPopularProducts();

    // ========== TOP RATED PRODUCTS ==========

    /**
     * Products with at least one review, from active and verified vendors only,
     * sorted by review count descending.
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.adminVisible = true AND p.vendor.isVerified = true AND p.vendor.isActive = true AND SIZE(p.reviews) > 0 ORDER BY SIZE(p.reviews) DESC")
    List<Product> findTopRatedProducts();

    // ========== CHEF SPECIALS ==========

    /**
     * Top products from African Kitchen or African Soups categories,
     * from active and verified vendors only, sorted by order count.
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.adminVisible = true AND p.vendor.isVerified = true AND p.vendor.isActive = true AND " +
            "(p.category.name = 'African Kitchen' OR p.category.name = 'African Soups') " +
            "ORDER BY SIZE(p.orderLines) DESC")
    List<Product> findChefSpecials();

    // ========== FEATURED PRODUCTS ==========

    /**
     * Products that had at least one order placed within the recency window (cutoff → now),
     * ranked by recent order count then total review count.
     * Returns a Page so the caller sets the SQL LIMIT via Pageable (no full-table scan in Java).
     * The service passes a pool size (e.g. 32) and then applies vendor-diversity capping.
     */
    @Query("""
            SELECT p FROM Product p
            JOIN p.orderLines ol
            WHERE p.available          = true
              AND p.adminVisible       = true
              AND p.vendor.isVerified  = true
              AND p.vendor.isActive    = true
              AND ol.order.createdAt  >= :cutoff
            GROUP BY p
            ORDER BY COUNT(ol) DESC,
                     SIZE(p.reviews)  DESC
            """)
    Page<Product> findFeaturedProducts(Pageable pageable, @Param("cutoff") LocalDateTime cutoff);

    /**
     * Broad fallback: no recency window — used when the platform is new
     * and there aren't enough recent orders to fill the featured section.
     * Ranked by all-time order count then review count.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.available         = true
              AND p.adminVisible      = true
              AND p.vendor.isVerified = true
              AND p.vendor.isActive   = true
              AND SIZE(p.orderLines)  > 0
            ORDER BY SIZE(p.orderLines) DESC,
                     SIZE(p.reviews)    DESC
            """)
    Page<Product> findFeaturedProductsBroad(Pageable pageable);

    /**
     * Zero-order fallback: returns available products from verified vendors
     * ranked by review count then newest — better than pure newest-first for
     * platforms that have reviews but no orders yet.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.available         = true
              AND p.adminVisible      = true
              AND p.vendor.isVerified = true
              AND p.vendor.isActive   = true
            ORDER BY SIZE(p.reviews) DESC,
                     p.createdAt     DESC
            """)
    Page<Product> findAnyFeaturedProducts(Pageable pageable);

    /**
     * Admin-pinned featured products — always surfaced first regardless of order history.
     * Ordered by most recently featured so freshly-pinned items appear at the top.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.available         = true
              AND p.adminVisible      = true
              AND p.vendor.isVerified = true
              AND p.vendor.isActive   = true
              AND p.isFeatured        = true
            ORDER BY p.featuredAt DESC
            """)
    List<Product> findAdminFeaturedProducts();

    // ========== PRODUCTS BY BEST RESTAURANTS NEAR ME ==========

    /**
     * Top products from active vendors in a specific city,
     * sorted by review count and order count.
     */
    @Query("""
                SELECT p FROM Product p
                JOIN p.vendor v
                JOIN v.address a
                LEFT JOIN v.reviews r
                LEFT JOIN p.orderLines ol
                WHERE p.available = true
                  AND p.adminVisible = true
                  AND v.isActive = true
                  AND v.isVerified = true
                  AND a.city = :city
                GROUP BY p.productId
                ORDER BY COUNT(DISTINCT r.reviewId) DESC,
                         COUNT(DISTINCT ol.orderLineId) DESC
                """)
    List<Product> findProductsByBestRestaurantsInCity(@Param("city") String city);

    // ========== MOST POPULAR PRODUCTS THIS MONTH ==========

    /**
     * Top products by order count within a time window,
     * from active and verified vendors only.
     */
    @Query("SELECT p FROM Product p JOIN p.orderLines ol JOIN ol.order o " +
            "WHERE p.available = true " +
            "AND p.adminVisible = true " +
            "AND p.vendor.isVerified = true " +
            "AND p.vendor.isActive = true " +
            "AND o.orderTime >= :startOfMonth " +
            "GROUP BY p " +
            "ORDER BY COUNT(ol) DESC")
    List<Product> findMostPopularProductsThisMonth(@Param("startOfMonth") LocalDateTime startOfMonth);

    /**
     * Top products by order count within a time window filtered by city,
     * from active and verified vendors only.
     */
    @Query("SELECT p FROM Product p JOIN p.orderLines ol JOIN ol.order o " +
            "WHERE p.available = true " +
            "AND p.adminVisible = true " +
            "AND p.vendor.isVerified = true " +
            "AND p.vendor.isActive = true " +
            "AND o.orderTime >= :startOfMonth " +
            "AND p.vendor.address.city = :city " +
            "GROUP BY p " +
            "ORDER BY COUNT(ol) DESC")
    List<Product> findMostPopularProductsThisMonthByCity(
            @Param("startOfMonth") LocalDateTime startOfMonth,
            @Param("city") String city);


    // ========== ADVANCED SEARCH ==========

    /**
     * Search available products by name only, from active and verified vendors.
     * Optionally filter by city — pass null to skip city filter.
     * scheduleType filtering is handled in the service layer (Hibernate 6 enum IS NULL limitation).
     */
    @Query("""
        SELECT p FROM Product p
        JOIN p.vendor v
        JOIN v.address a
        WHERE p.available = true
          AND p.adminVisible = true
          AND v.isVerified = true
          AND v.isActive = true
          AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (:city IS NULL OR LOWER(a.city) = LOWER(:city))
          AND (:categoryId IS NULL OR p.category.categoryId = :categoryId)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:isVegetarian IS NULL OR p.isVegetarian = :isVegetarian)
          AND (:isVegan IS NULL OR p.isVegan = :isVegan)
          AND (:isGlutenFree IS NULL OR p.isGlutenFree = :isGlutenFree)
        ORDER BY SIZE(p.orderLines) DESC
        """)
    List<Product> findByFilters(
            @Param("name")         String name,
            @Param("city")         String city,
            @Param("categoryId")   Long categoryId,
            @Param("minPrice")     BigDecimal minPrice,
            @Param("maxPrice")     BigDecimal maxPrice,
            @Param("isVegetarian") Boolean isVegetarian,
            @Param("isVegan")      Boolean isVegan,
            @Param("isGlutenFree") Boolean isGlutenFree
    );

    /**
     * Haversine formula in JPQL — finds products from vendors within
     * :radiusKm of the given coordinates.
     * 6371 = Earth's radius in km.
     */
    @Query("""
        SELECT p FROM Product p
        JOIN p.vendor v
        JOIN v.address a
        WHERE p.available = true
          AND p.adminVisible = true
          AND v.isVerified = true
          AND v.isActive = true
          AND a.latitude IS NOT NULL
          AND a.longitude IS NOT NULL
          AND (6371 * ACOS(
                COS(RADIANS(:lat)) * COS(RADIANS(a.latitude)) *
                COS(RADIANS(a.longitude) - RADIANS(:lng)) +
                SIN(RADIANS(:lat)) * SIN(RADIANS(a.latitude))
              )) <= :radiusKm
        ORDER BY (6371 * ACOS(
                COS(RADIANS(:lat)) * COS(RADIANS(a.latitude)) *
                COS(RADIANS(a.longitude) - RADIANS(:lng)) +
                SIN(RADIANS(:lat)) * SIN(RADIANS(a.latitude))
              )) ASC
        """)
    List<Product> findProductsNearCoordinates(
            @Param("lat")      double lat,
            @Param("lng")      double lng,
            @Param("radiusKm") double radiusKm
    );

    // ========== ADMIN FEATURED FILTER ==========

    /**
     * Used by the admin "Featured" / "Not Featured" tab filter.
     * Returns a page of products matching the given isFeatured value,
     * ordered by featuredAt DESC (pinned most recently first).
     */
    Page<Product> findByIsFeaturedOrderByFeaturedAtDesc(Boolean isFeatured, Pageable pageable);

    // ========== ADMIN ALL PRODUCTS ==========

    /**
     * All products for the admin product management page.
     * Includes unavailable products and unverified vendors so admins can see everything.
     * Ordered: featured first (featuredAt DESC), then newest.
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.vendor v
        LEFT JOIN FETCH p.category c
        ORDER BY p.isFeatured DESC,
                 p.featuredAt DESC NULLS LAST,
                 p.createdAt  DESC
        """)
    Page<Product> findAllForAdmin(Pageable pageable);

    /**
     * Admin product search — case-insensitive partial match across product name,
     * vendor name, and category name. Includes all products regardless of
     * availability or verification status.
     * Ordered: featured first, then by how early in the name the match appears
     * (LOCATE gives position of match — lower = closer to start = more relevant),
     * then alphabetically.
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.vendor v
        LEFT JOIN p.category c
        WHERE LOWER(p.name)        LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(v.restaurantName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(c.name)        LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY p.isFeatured DESC,
                 LOCATE(LOWER(:query), LOWER(p.name)) ASC,
                 p.name ASC
        """)
    Page<Product> searchForAdmin(@Param("query") String query, Pageable pageable);

    /**
     * Admin search filtered by featured status — combines search and tab filter.
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.vendor v
        LEFT JOIN p.category c
        WHERE p.isFeatured = :featured
          AND (LOWER(p.name)           LIKE LOWER(CONCAT('%', :query, '%'))
           OR  LOWER(v.restaurantName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR  LOWER(c.name)           LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY p.isFeatured DESC,
                 LOCATE(LOWER(:query), LOWER(p.name)) ASC,
                 p.name ASC
        """)
    Page<Product> searchForAdminWithFeatured(
            @Param("query")    String query,
            @Param("featured") Boolean featured,
            Pageable pageable);

    // ========== ADMIN VISIBILITY FILTER ==========

    /** Filter all products by adminVisible (true = platform-visible, false = suspended). */
    Page<Product> findByAdminVisible(Boolean adminVisible, Pageable pageable);

    /** Admin search filtered by adminVisible status. */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.vendor v
        LEFT JOIN p.category c
        WHERE p.adminVisible = :adminVisible
          AND (LOWER(p.name)           LIKE LOWER(CONCAT('%', :query, '%'))
           OR  LOWER(v.restaurantName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR  LOWER(c.name)           LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY LOCATE(LOWER(:query), LOWER(p.name)) ASC,
                 p.name ASC
        """)
    Page<Product> searchForAdminWithAdminVisible(
            @Param("query")        String query,
            @Param("adminVisible") Boolean adminVisible,
            Pageable pageable);

    /**
     * Admin use — returns ALL featured products regardless of visibility or availability.
     * Used by clearAllFeatured() so suspended-but-featured products are also unpinned.
     */
    @Query("SELECT p FROM Product p WHERE p.isFeatured = true")
    List<Product> findAllFeaturedForAdmin();

    // ========== COUNTS ==========

    Long countByVendor(VendorProfile vendor);

    Long countByVendorAndAvailable(VendorProfile vendor, Boolean available);

    Long countByCategory(Category category);

    Long countByAvailable(Boolean available);

    Long countByAvailableTrue();
}