package com.afrochow.category.model;
import com.afrochow.product.model.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "category", indexes = {
        @Index(name = "idx_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    private String iconUrl;

    // Display order (for sorting on UI)
    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true; // Can be disabled by admin

    // ========== TIMESTAMPS ==========

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ========== RELATIONSHIPS ==========

    // Products in this category (ONE-TO-MANY)
    // One category contains many products
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    // ========== HELPER METHODS ==========

    // Count products in this category
    @Transient
    public int getProductCount() {
        return products != null ? products.size() : 0;
    }

    // Count active (available) products
    @Transient
    public int getActiveProductCount() {
        if (products == null) return 0;
        return (int) products.stream()
                .filter(Product::getAvailable)
                .count();
    }

    // Check if category has products
    @Transient
    public boolean hasProducts() {
        return products != null && !products.isEmpty();
    }
}