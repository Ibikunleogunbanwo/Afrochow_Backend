package com.afrochow.favorite.repository;

import com.afrochow.common.enums.FavoriteType;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.favorite.model.Favorite;
import com.afrochow.product.model.Product;
import com.afrochow.vendor.model.VendorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // ========== FIND BY CUSTOMER ==========

    /**
     * Get all favorites for a customer
     */
    List<Favorite> findByCustomerOrderByCreatedAtDesc(CustomerProfile customer);

    /**
     * Get all favorites of a specific type for a customer
     */
    List<Favorite> findByCustomerAndFavoriteTypeOrderByCreatedAtDesc(
            CustomerProfile customer, FavoriteType favoriteType);

    // ========== CHECK IF FAVORITED ==========

    /**
     * Check if customer has favorited a specific vendor
     */
    boolean existsByCustomerAndVendor(CustomerProfile customer, VendorProfile vendor);

    /**
     * Check if customer has favorited a specific product
     */
    boolean existsByCustomerAndProduct(CustomerProfile customer, Product product);

    // ========== FIND SPECIFIC FAVORITE ==========

    /**
     * Find favorite by customer and vendor
     */
    Optional<Favorite> findByCustomerAndVendor(CustomerProfile customer, VendorProfile vendor);

    /**
     * Find favorite by customer and product
     */
    Optional<Favorite> findByCustomerAndProduct(CustomerProfile customer, Product product);

    // ========== STATISTICS ==========

    /**
     * Count total favorites for a vendor
     */
    Long countByVendor(VendorProfile vendor);

    /**
     * Count total favorites for a product
     */
    Long countByProduct(Product product);

    /**
     * Count how many customers have favorited this vendor
     */
    @Query("SELECT COUNT(DISTINCT f.customer) FROM Favorite f WHERE f.vendor = :vendor")
    Long countDistinctCustomersByVendor(VendorProfile vendor);

    /**
     * Count total favorites by customer
     */
    Long countByCustomer(CustomerProfile customer);

    /**
     * Count favorites of a specific type by customer
     */
    Long countByCustomerAndFavoriteType(CustomerProfile customer, FavoriteType favoriteType);

    // ========== DELETE ==========

    /**
     * Delete favorite by customer and vendor
     */
    void deleteByCustomerAndVendor(CustomerProfile customer, VendorProfile vendor);

    /**
     * Delete favorite by customer and product
     */
    void deleteByCustomerAndProduct(CustomerProfile customer, Product product);
}
