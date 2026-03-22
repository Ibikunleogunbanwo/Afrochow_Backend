package com.afrochow.promotion.model;

import com.afrochow.common.enums.PromotionType;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "promotion", indexes = {
        @Index(name = "idx_promo_code",      columnList = "code"),
        @Index(name = "idx_promo_public_id", columnList = "publicPromotionId"),
        @Index(name = "idx_promo_active",    columnList = "isActive")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promotionId;

    @Column(unique = true, nullable = false)
    private String publicPromotionId;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromotionType type;

    /** Percentage (0-100) for PERCENTAGE type, or dollar amount for FIXED_AMOUNT. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** Maximum discount cap for PERCENTAGE promotions (nullable = no cap). */
    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Minimum subtotal required to use this promotion (nullable = no minimum). */
    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount;

    /** Total number of times this code can be used globally (nullable = unlimited). */
    private Integer usageLimit;

    /** Max times a single user can use this code (nullable = unlimited). */
    private Integer perUserLimit;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Vendor-specific promotion — null means the promotion applies to all vendors.
     */
    @ManyToOne
    @JoinColumn(name = "vendor_profile_id")
    private VendorProfile vendor;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void onPrePersist() {
        if (this.publicPromotionId == null) {
            this.publicPromotionId = "PROMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (this.code != null) {
            this.code = this.code.toUpperCase().trim();
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        if (this.code != null) {
            this.code = this.code.toUpperCase().trim();
        }
    }

    @Transient
    public boolean isCurrentlyActive() {
        if (!Boolean.TRUE.equals(isActive)) return false;
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }
}
