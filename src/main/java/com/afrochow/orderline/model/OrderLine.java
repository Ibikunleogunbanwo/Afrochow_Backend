package com.afrochow.orderline.model;

import com.afrochow.order.model.Order;
import com.afrochow.product.model.Product;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_line", indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_product_id", columnList = "product_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderLineId;

    // ========== RELATIONSHIPS ==========

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // ========== ORDER DETAILS (SNAPSHOT) ==========

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(nullable = false, length = 200)
    private String productNameAtPurchase;

    @Column(length = 500)
    private String productDescriptionAtPurchase;

    @Column(length = 500)
    private String specialInstructions;

    // ========== SNAPSHOT BEFORE SAVING ==========

    @PrePersist
    public void captureProductSnapshot() {
        if (this.product != null) {
            if (this.productNameAtPurchase == null) {
                this.productNameAtPurchase = this.product.getName();
            }
            if (this.productDescriptionAtPurchase == null) {
                this.productDescriptionAtPurchase = this.product.getDescription();
            }
            if (this.priceAtPurchase == null) {
                this.priceAtPurchase = this.product.getPrice();
            }
        }
    }

    // ========== HELPER METHODS ==========

    @Transient
    public BigDecimal getLineTotal() {
        return priceAtPurchase.multiply(BigDecimal.valueOf(quantity));
    }

    @Transient
    public String getDisplayName() {
        if (quantity > 1) {
            return quantity + "x " + productNameAtPurchase;
        }
        return productNameAtPurchase;
    }

    @Transient
    public boolean hasSpecialInstructions() {
        return specialInstructions != null && !specialInstructions.isEmpty();
    }
}
