package com.afrochow.vendor.repository;

import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.common.enums.Province;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorProfileRepository extends JpaRepository<VendorProfile, Long> {

    // ========== FIND BY USER ==========

    Optional<VendorProfile> findByUser_UserId(Long userId);

    Optional<VendorProfile> findByStripeAccountId(String stripeAccountId);

    Optional<VendorProfile> findByUser_PublicUserId(String publicUserId);

    Optional<VendorProfile> findByUser_Username(String username);

    // ========== FIND BY PUBLIC VENDOR ID ==========
    // publicVendorId is @Transient (delegates to user.publicUserId) — no DB column,
    // so a derived query is not possible; delegate to the user path instead.
    default Optional<VendorProfile> findByPublicVendorId(String publicVendorId) {
        return findByUser_PublicUserId(publicVendorId);
    }

    // ========== SEARCH BY NAME ==========

    List<VendorProfile> findByRestaurantNameContainingIgnoreCase(String name);

    // ========== FILTER BY STATUS ==========

    List<VendorProfile> findByIsVerifiedAndIsActive(Boolean isVerified, Boolean isActive);

    List<VendorProfile> findByIsVerified(Boolean isVerified);

    List<VendorProfile> findByIsActive(Boolean isActive);

    // ========== SEARCH BY CUISINE ==========

    List<VendorProfile> findByCuisineTypeIgnoreCase(String cuisineType);

    List<VendorProfile> findByCuisineTypeContainingIgnoreCase(String cuisineType);

    // ========== ACTIVE + VERIFIED ==========

    // Name reflects the query accurately — the actual open-now check is @Transient
    // and must be evaluated in-memory in SearchService.getOpenVendors().
    @Query("SELECT v FROM VendorProfile v WHERE v.isActive = true AND v.isVerified = true")
    List<VendorProfile> findActiveAndVerifiedVendors();

    // ========== JOIN FETCH (prevents N+1) ==========

    // Used by getPopularCuisines() — loads vendors + their products in one query.
    // LEFT JOIN FETCH keeps vendors with zero products; DISTINCT removes join duplicates.
    @Query("SELECT DISTINCT v FROM VendorProfile v LEFT JOIN FETCH v.products WHERE v.isActive = :isActive")
    List<VendorProfile> findByIsActiveWithProducts(@Param("isActive") Boolean isActive);

    // ========== TOP RATED VENDORS ==========

    @Query("""
            SELECT v FROM VendorProfile v
            WHERE v.isActive = true
              AND v.isVerified = true
              AND (SELECT COUNT(r) FROM Review r WHERE r.vendor = v AND r.isVisible = true) >= :minReviews
            ORDER BY (SELECT COALESCE(AVG(CAST(r.rating AS double)), 0.0) FROM Review r WHERE r.vendor = v AND r.isVisible = true) DESC
            """)
    Page<VendorProfile> findTopRatedVendors(@Param("minReviews") int minReviews, Pageable pageable);

    // ========== FIND BY LOCATION ==========

    @Query("""
            SELECT v FROM VendorProfile v JOIN v.address a
            WHERE a.city = :city
              AND v.isActive = true
              AND v.isVerified = true
            ORDER BY v.totalOrdersCompleted DESC
            """)
    List<VendorProfile> findByCity(@Param("city") String city);

    @Query("SELECT v FROM VendorProfile v JOIN v.address a WHERE a.province = :province AND v.isActive = true AND v.isVerified = true")
    List<VendorProfile> findByProvince(@Param("province") Province province);

    // ========== FIND BY COORDINATES (HAVERSINE) ==========

    // Native SQL: JPQL has no alias support for computed expressions, so the
    // Haversine formula would have to be duplicated in both WHERE and ORDER BY
    // when written as JPQL. Native SQL is identical in behaviour but is testable
    // directly in a DB client and allows a derived-table refactor in the future.
    //
    // NOTE: DISTINCT is intentionally omitted. vendor_profile has a single address_id
    // FK, making the JOIN 1-to-1 — each vendor appears exactly once in the result.
    // Using DISTINCT with ORDER BY on a computed expression referencing a joined table
    // column triggers a MySQL strict-mode error:
    //   "Expression #1 of ORDER BY clause is not in SELECT list, references column ..."
    @Query(value = """
            SELECT v.*
            FROM vendor_profile v
            JOIN address a ON a.address_id = v.address_id
            WHERE v.is_active   = true
              AND v.is_verified = true
              AND a.latitude    IS NOT NULL
              AND a.longitude   IS NOT NULL
              AND (6371 * ACOS(LEAST(1.0,
                    COS(RADIANS(:lat)) * COS(RADIANS(a.latitude))
                    * COS(RADIANS(a.longitude) - RADIANS(:lng))
                    + SIN(RADIANS(:lat)) * SIN(RADIANS(a.latitude))
                  ))) <= :radiusKm
            ORDER BY (6371 * ACOS(LEAST(1.0,
                    COS(RADIANS(:lat)) * COS(RADIANS(a.latitude))
                    * COS(RADIANS(a.longitude) - RADIANS(:lng))
                    + SIN(RADIANS(:lat)) * SIN(RADIANS(a.latitude))
                  ))) ASC,
                  v.total_orders_completed DESC
            """, nativeQuery = true)
    List<VendorProfile> findVendorsNearCoordinates(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm
    );

    // ========== COUNTS ==========

    @Query("SELECT COUNT(v) FROM VendorProfile v WHERE v.isActive = true")
    long countActiveVendors();

    @Query("SELECT COUNT(v) FROM VendorProfile v WHERE v.isActive = true AND v.isVerified = true")
    long countActiveAndVerifiedVendors();
}
