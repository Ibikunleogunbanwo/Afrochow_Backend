package com.afrochow.favorite.model;

import com.afrochow.common.enums.FavoriteType;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.product.model.Product;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing a customer's favorite vendor or product
 *
 * A customer can favorite:
 * - Vendors (restaurants) for quick access
 * - Products for wishlist/quick reorder
 *
 * Each favorite is unique per customer-vendor or customer-product pair
 */
@Entity
@Table(name = "favorite",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"customer_profile_id", "vendor_profile_id"}),
           @UniqueConstraint(columnNames = {"customer_profile_id", "product_id"})
       },
       indexes = {
           @Index(name = "idx_favorite_customer", columnList = "customer_profile_id"),
           @Index(name = "idx_favorite_vendor", columnList = "vendor_profile_id"),
           @Index(name = "idx_favorite_product", columnList = "product_id"),
           @Index(name = "idx_favorite_type", columnList = "favorite_type")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long favoriteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_profile_id", nullable = false)
    private CustomerProfile customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "favorite_type", nullable = false)
    private FavoriteType favoriteType;

    // For VENDOR favorites
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id")
    private VendorProfile vendor;

    // For PRODUCT favorites
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ========== LIFECYCLE CALLBACKS ==========

    /**
     * Initialize and validate on creation
     */
    @PrePersist
    protected void onCreate() {
        // Set timestamp
        createdAt = LocalDateTime.now();

        // Validate
        validate();
    }

    /**
     * Validate on update
     */
    @PreUpdate
    private void onUpdate() {
        validate();
    }

    // ========== VALIDATION ==========

    /**
     * Validate that exactly one of vendor or product is set based on type
     */
    private void validate() {
        if (favoriteType == FavoriteType.VENDOR && vendor == null) {
            throw new IllegalStateException("Vendor must be set for VENDOR favorite type");
        }
        if (favoriteType == FavoriteType.PRODUCT && product == null) {
            throw new IllegalStateException("Product must be set for PRODUCT favorite type");
        }
        if (favoriteType == FavoriteType.VENDOR && product != null) {
            throw new IllegalStateException("Product must be null for VENDOR favorite type");
        }
        if (favoriteType == FavoriteType.PRODUCT && vendor != null) {
            throw new IllegalStateException("Vendor must be null for PRODUCT favorite type");
        }
    }
}
