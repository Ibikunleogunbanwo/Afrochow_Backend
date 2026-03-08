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

    Optional<Product> findByPublicProductId(String publicProductId);


    List<Product> findByVendor(VendorProfile vendor);

    List<Product> findByVendorAndAvailable(VendorProfile vendor, Boolean available);


    List<Product> findByCategory(Category category);

    List<Product> findByCategoryAndAvailable(Category category, Boolean available);


    List<Product> findByNameContainingIgnoreCaseAndAvailable(String name, Boolean available);

    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String name, String description);


    List<Product> findByAvailable(Boolean available);


    List<Product> findByPriceBetweenAndAvailable(BigDecimal minPrice, BigDecimal maxPrice, Boolean available);


    List<Product> findByIsVegetarianAndAvailable(Boolean isVegetarian, Boolean available);

    List<Product> findByIsVeganAndAvailable(Boolean isVegan, Boolean available);

    List<Product> findByIsGlutenFreeAndAvailable(Boolean isGlutenFree, Boolean available);


    @Query("SELECT p FROM Product p ORDER BY SIZE(p.orderLines) DESC")
    List<Product> findPopularProducts();


    @Query("SELECT p FROM Product p WHERE p.available = true AND SIZE(p.reviews) > 0 ORDER BY SIZE(p.reviews) DESC")
    List<Product> findTopRatedProducts();

    @Query("SELECT p FROM Product p WHERE p.available = true AND " +
           "(p.category.name = 'African Kitchen' OR p.category.name = 'African Soups') " +
           "ORDER BY SIZE(p.orderLines) DESC")
    List<Product> findChefSpecials();

    @Query("SELECT p FROM Product p WHERE p.available = true AND p.vendor.isVerified = true " +
           "ORDER BY SIZE(p.orderLines) DESC, SIZE(p.reviews) DESC")
    List<Product> findFeaturedProducts();

    @Query("""
                SELECT p FROM Product p
                JOIN p.vendor v
                JOIN v.address a
                LEFT JOIN v.reviews r
                LEFT JOIN p.orderLines ol
                WHERE p.available = true
                  AND v.isActive = true
                  AND a.city = :city
                GROUP BY p.productId
                ORDER BY COUNT(DISTINCT r.reviewId) DESC,
                         COUNT(DISTINCT ol.orderLineId) DESC
                """)
    List<Product> findProductsByBestRestaurantsInCity(@Param("city") String city);


    @Query("SELECT p FROM Product p JOIN p.orderLines ol JOIN ol.order o " +
           "WHERE p.available = true " +
           "AND o.orderTime >= :startOfMonth " +
           "GROUP BY p " +
           "ORDER BY COUNT(ol) DESC")
    List<Product> findMostPopularProductsThisMonth(LocalDateTime startOfMonth);

    @Query("SELECT p FROM Product p JOIN p.orderLines ol JOIN ol.order o " +
           "WHERE p.available = true " +
           "AND o.orderTime >= :startOfMonth " +
           "AND p.vendor.address.city = :city " +
           "GROUP BY p " +
           "ORDER BY COUNT(ol) DESC")
    List<Product> findMostPopularProductsThisMonthByCity(LocalDateTime startOfMonth, String city);

    Long countByVendor(VendorProfile vendor);

    Long countByVendorAndAvailable(VendorProfile vendor, Boolean available);

    Long countByCategory(Category category);

    Long countByAvailable(Boolean available);

    Long countByAvailableTrue();
}