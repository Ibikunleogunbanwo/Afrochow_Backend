package com.afrochow.promotion.repository;

import com.afrochow.promotion.model.Promotion;
import com.afrochow.promotion.model.PromotionUsage;
import com.afrochow.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    long countByPromotion(Promotion promotion);

    void deleteByPromotion(Promotion promotion);

    long countByPromotionAndUser(Promotion promotion, User user);

    List<PromotionUsage> findByUser(User user);

    List<PromotionUsage> findByPromotion(Promotion promotion);

    @Query("SELECT COALESCE(SUM(pu.discountApplied), 0) FROM PromotionUsage pu")
    BigDecimal calculateTotalDiscountGiven();
}
