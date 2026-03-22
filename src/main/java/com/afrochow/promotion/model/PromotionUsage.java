package com.afrochow.promotion.model;

import com.afrochow.order.model.Order;
import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_usage", indexes = {
        @Index(name = "idx_usage_promotion", columnList = "promotion_id"),
        @Index(name = "idx_usage_user",      columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long usageId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountApplied;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime usedAt = LocalDateTime.now();
}
