package com.afrochow.promotion.repository;

import com.afrochow.promotion.model.Promotion;
import com.afrochow.vendor.model.VendorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCode(String code);

    Optional<Promotion> findByPublicPromotionId(String publicPromotionId);

    List<Promotion> findByIsActiveTrue();

    @Query("""
            SELECT p FROM Promotion p
            WHERE p.isActive = true
              AND p.startDate <= :now
              AND p.endDate   >= :now
            """)
    List<Promotion> findAllCurrentlyActive(@Param("now") LocalDateTime now);

    @Query("""
            SELECT p FROM Promotion p
            WHERE p.isActive = true
              AND p.startDate <= :now
              AND p.endDate   >= :now
              AND (p.vendor IS NULL OR p.vendor.user.publicUserId = :vendorPublicId)
            """)
    List<Promotion> findActiveForVendor(
            @Param("now") LocalDateTime now,
            @Param("vendorPublicId") String vendorPublicId);

    List<Promotion> findByVendor(VendorProfile vendor);

    boolean existsByCode(String code);
}
