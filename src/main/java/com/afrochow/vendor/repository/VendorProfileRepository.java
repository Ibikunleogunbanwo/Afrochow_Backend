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

    Optional<VendorProfile> findByUser_PublicUserId(String publicUserId);

    Optional<VendorProfile> findByUser_Username(String username);

    // ========== FIND BY PUBLIC VENDOR ID ==========
    // Alias used by AdminVendorManagementController
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

    // ========== FIND OPEN VENDORS ==========

    @Query("SELECT v FROM VendorProfile v WHERE v.isActive = true AND v.isVerified = true")
    List<VendorProfile> findOpenVendors();

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
            ORDER BY v.averageRating DESC, v.totalOrdersCompleted DESC
            """)
    List<VendorProfile> findByCity(@Param("city") String city);

    @Query("SELECT v FROM VendorProfile v JOIN v.address a WHERE a.province = :province AND v.isActive = true AND v.isVerified = true")
    List<VendorProfile> findByProvince(@Param("province") Province province);

    // ========== FIND BY COORDINATES (HAVERSINE) ==========

    /**
     * Returns distinct verified+active vendors within radiusKm of the given lat/lng,
     * ordered by ascending distance. Mirrors the Haversine logic in ProductRepository.
     */
    @Query("""
            SELECT DISTINCT v FROM VendorProfile v
            JOIN v.address a
            WHERE v.isActive  = true
              AND v.isVerified = true
              AND a.latitude   IS NOT NULL
              AND a.longitude  IS NOT NULL
              AND (6371 * ACOS(LEAST(1.0,
                    COS(RADIANS(:lat)) * COS(RADIANS(a.latitude))  *
                    COS(RADIANS(a.longitude) - RADIANS(:lng)) +
                    SIN(RADIANS(:lat)) * SIN(RADIANS(a.latitude))
                  ))) <= :radiusKm
            ORDER BY (6371 * ACOS(LEAST(1.0,
                    COS(RADIANS(:lat)) * COS(RADIANS(a.latitude))  *
                    COS(RADIANS(a.longitude) - RADIANS(:lng)) +
                    SIN(RADIANS(:lat)) * SIN(RADIANS(a.latitude))
                  ))) ASC,
                  v.averageRating DESC,
                  v.totalOrdersCompleted DESC
            """)
    List<VendorProfile> findVendorsNearCoordinates(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm
    );

    // ========== COUNTS ==========

    @Query("SELECT COUNT(v) FROM VendorProfile v WHERE v.isActive = true")
    long countActiveVendors();

    // Alias used by StatsService
    default long countByIsActiveTrue() {
        return countActiveVendors();
    }

    @Query("SELECT COUNT(v) FROM VendorProfile v WHERE v.isActive = true AND v.isVerified = true")
    long countActiveAndVerifiedVendors();

    // Alias used by StatsService
    default long countByIsVerifiedTrueAndIsActiveTrue() {
        return countActiveAndVerifiedVendors();
    }
}