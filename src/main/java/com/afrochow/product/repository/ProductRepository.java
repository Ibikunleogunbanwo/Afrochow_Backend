package com.afrochow.product.repository;

import com.afrochow.product.model.Product;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.category.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ========== FIND BY ID ==========

    Optional<Product> findByPublicProductId(String publicProductId);

    // ========== FIND BY VENDOR ==========

    List<Product> findByVendor(VendorProfile vendor);

    List<Product> findByVendorAndAvailable(VendorProfile vendor, Boolean available);

    // ========== FIND BY CATEGORY ==========

    List<Product> findByCategory(Category category);

    List<Product> findByCategoryAndAvailable(Category category, Boolean available);

    // ========== SEARCH BY NAME / DESCRIPTION ==========

    List<Product> findByNameContainingIgnoreCaseAndAvailable(String name, Boolean available);

    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String name, String description);

    // ========== FIND BY AVAILABILITY ==========

    List<Product> findByAvailable(Boolean available);

    // ========== FIND BY PRICE RANGE ==========

    List<Product> findByPriceBetweenAndAvailable(BigDecimal minPrice, BigDecimal maxPrice, Boolean available);

    // ========== FIND BY DIETARY ==========

    List<Product> findByIsVegetarianAndAvailable(Boolean isVegetarian, Boolean available);

    List<Product> findByIsVeganAndAvailable(Boolean isVegan, Boolean available);

    List<Product> findByIsGlutenFreeAndAvailable(Boolean isGlutenFree, Boolean available);

    // ========== POPULAR PRODUCTS ==========

    /**
     * Products sorted by order count from active and verified vendors only.
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.vendor.isVerified = true AND p.vendor.isActive = true ORDER BY SIZE(p.orderLines) DESC")
    List<Product> findPopularProducts();

    // ========== TOP RATED PRODUCTS ==========

    /**
     * Products with at least one review, from active and verified vendors only,
     * sorted by review count descending.
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.vendor.isVerified = true AND p.vendor.isActive = true AND SIZE(p.reviews) > 0 ORDER BY SIZE(p.reviews) DESC")
    List<Product> findTopRatedProducts();

    // ========== CHEF SPECIALS ==========

    /**
     * Top products from African Kitchen or African Soups categories,
     * from active and verified vendors only, sorted by order count.
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.vendor.isVerified = true AND p.vendor.isActive = true AND " +
            "(p.category.name = 'African Kitchen' OR p.category.name = 'African Soups') " +
            "ORDER BY SIZE(p.orderLines) DESC")
    List<Product> findChefSpecials();

    // ========== FEATURED PRODUCTS ==========

    /**
     * Top products from active and verified vendors only,
     * sorted by order count then review count.
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.vendor.isVerified = true AND p.vendor.isActive = true " +
            "ORDER BY SIZE(p.orderLines) DESC, SIZE(p.reviews) DESC")
    List<Product> findFeaturedProducts();

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
     */
    @Query("""
        SELECT p FROM Product p
        JOIN p.vendor v
        JOIN v.address a
        WHERE p.available = true
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

    // ========== COUNTS ==========

    Long countByVendor(VendorProfile vendor);

    Long countByVendorAndAvailable(VendorProfile vendor, Boolean available);

    Long countByCategory(Category category);

    Long countByAvailable(Boolean available);

    Long countByAvailableTrue();
}