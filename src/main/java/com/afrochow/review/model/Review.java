package com.afrochow.review.model;
import com.afrochow.order.model.Order;
import com.afrochow.product.model.Product;
import com.afrochow.user.model.User;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "review", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_vendor_id", columnList = "vendor_profile_id"),
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_rating", columnList = "rating")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    // ========== RELATIONSHIPS ==========

    // Who wrote this review? (MANY-TO-ONE)
    // Many reviews can be written by one user
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Which vendor is being reviewed? (MANY-TO-ONE)
    // Many reviews can be about one vendor
    @ManyToOne
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendor;

    // Which product is being reviewed? (MANY-TO-ONE, OPTIONAL)
    // If null, this is a general vendor review
    // If set, this is a specific product review
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    // ========== REVIEW CONTENT ==========

    // Rating: 1-5 stars (validated!)
    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private Integer rating;

    // Written comment (optional)
    @Column(length = 1000)
    private String comment;

    // Was this review helpful? (can be voted on)
    @Column(nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;

    // Is this review visible? (can be hidden by admin)
    @Column(nullable = false)
    @Builder.Default
    private Boolean isVisible = true;

    // Optional: Link to order (proof of purchase)
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    // ========== TIMESTAMPS ==========

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt; // If user edits review

    // ========== HELPER METHODS ==========

    // Is this a product-specific review?
    @Transient
    public boolean isProductReview() {
        return product != null;
    }

    // Is this a general vendor review?
    @Transient
    public boolean isVendorReview() {
        return product == null;
    }

    // Get star rating as string (e.g., "★★★★★")
    @Transient
    public String getStarRating() {
        return "★".repeat(rating) + "☆".repeat(5 - rating);
    }

    // Check if user can edit this review (within 24 hours)
    @Transient
    public boolean canBeEdited() {
        return LocalDateTime.now().isBefore(createdAt.plusHours(24));
    }

    // Mark review as helpful
    public void markAsHelpful() {
        this.helpfulCount++;
    }

    // Update review content
    public void updateReview(Integer newRating, String newComment) {
        this.rating = newRating;
        this.comment = newComment;
        this.updatedAt = LocalDateTime.now();
    }

    // Hide review (admin action)
    public void hide() {
        this.isVisible = false;
    }

    // Show review (admin action)
    public void show() {
        this.isVisible = true;
    }
}