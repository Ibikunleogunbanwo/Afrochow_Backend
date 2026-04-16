package com.afrochow.product.model;

import com.afrochow.category.model.Category;
import com.afrochow.common.enums.ScheduleType;
import com.afrochow.favorite.model.Favorite;
import com.afrochow.orderline.model.OrderLine;
import com.afrochow.review.model.Review;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_public_product_id", columnList = "publicProductId"),
        @Index(name = "idx_vendor_id", columnList = "vendor_profile_id"),
        @Index(name = "idx_category_id", columnList = "category_id"),
        @Index(name = "idx_available", columnList = "available"),
        @Index(name = "idx_admin_visible", columnList = "adminVisible")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    /** Optimistic-locking version — prevents concurrent availability-toggle races.
     *  Primitive long: a null DB value is read as 0, never propagated as null. */
    @Version
    private long version;

    @Column(unique = true, nullable = false)
    private String publicProductId;

    // ========== PRODUCT INFORMATION ==========
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;

    /**
     * Platform-controlled visibility flag — set by admins only.
     * When false, the product is suspended from customer view regardless of the vendor's
     * own {@code available} flag. Vendors cannot override this.
     * Use case: policy enforcement, contraband removal, compliance holds.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean adminVisible = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer preparationTimeMinutes = 20;

    // ========== FULFILMENT SCHEDULE ==========

    /**
     * SAME_DAY: order any time, ready in preparationTimeMinutes.
     * ADVANCE_ORDER: must be placed advanceNoticeHours before the requested fulfilment time.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'SAME_DAY'")
    @Builder.Default
    private ScheduleType scheduleType = ScheduleType.SAME_DAY;

    /**
     * Required when scheduleType = ADVANCE_ORDER.
     * Minimum hours of notice the vendor needs before the requested fulfilment time (e.g. 24, 48).
     */
    private Integer advanceNoticeHours;

    // ========== NUTRITIONAL INFO ==========
    private Integer calories;
    private Boolean isVegetarian;
    private Boolean isVegan;
    private Boolean isGlutenFree;
    private Boolean isSpicy;

    // ========== FEATURED ==========
    /** Admin-pinned: always surfaces in the featured section regardless of order history. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    /** When an admin last pinned this product as featured. */
    private LocalDateTime featuredAt;

    // ========== TIMESTAMPS ==========
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ========== RELATIONSHIPS ==========
    @ManyToOne
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendor;

    // ON DELETE SET NULL: deleting a Category nulls this column rather than deleting the product.
    // Products are owned by VendorProfile, not Category.
    @ManyToOne
    @JoinColumn(name = "category_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Category category;

    @OneToMany(mappedBy = "product")
    @Builder.Default
    private List<OrderLine> orderLines = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Favorite> favorites = new ArrayList<>();

    // ========== AUTO-GENERATE PUBLIC ID ==========
    @PrePersist
    public void onPrePersist() {
        if (this.publicProductId == null) {
            if (vendor == null || vendor.getPublicVendorId() == null) {
                throw new IllegalStateException("Vendor must be set with a valid publicId before persisting a product.");
            }
            this.publicProductId = "PROD-" + vendor.getPublicVendorId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    // ========== HELPER METHODS ==========
    @Transient
    public Double getAverageRating() {
        if (reviews == null || reviews.isEmpty()) return 0.0;
        return reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
    }

    @Transient
    public int getReviewCount() {
        return reviews != null ? reviews.size() : 0;
    }

    @Transient
    public int getTotalOrders() {
        return orderLines != null ? orderLines.size() : 0;
    }
}
