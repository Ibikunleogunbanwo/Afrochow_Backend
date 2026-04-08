package com.afrochow.promotion.repository;

import com.afrochow.promotion.model.Promotion;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCode(String code);

    /**
     * Fetch a promotion by its code with a database-level write lock.
     * Use this in calculateDiscount() and recordUsage() so that the usage-count
     * check and the subsequent PromotionUsage INSERT are serialised — preventing
     * two concurrent requests from both passing the limit guard and both recording a use.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.code = :code")
    Optional<Promotion> findByCodeWithLock(@Param("code") String code);

    Optional<Promotion> findByPublicPromotionId(String publicPromotionId);

    List<Promotion> findByIsActiveTrue();

    /**
     * All currently active promotions (including vendor-specific ones).
     * Use for admin visibility only — not for the public customer-facing endpoint.
     */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.isActive = true
              AND p.startDate <= :now
              AND p.endDate   >= :now
            """)
    List<Promotion> findAllCurrentlyActive(@Param("now") LocalDateTime now);

    /**
     * Global promotions only (vendor IS NULL) — used by the public customer endpoint
     * so vendor-specific codes are never leaked to customers browsing other vendors.
     */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.isActive = true
              AND p.startDate <= :now
              AND p.endDate   >= :now
              AND p.vendor IS NULL
            """)
    List<Promotion> findGlobalCurrentlyActive(@Param("now") LocalDateTime now);

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
