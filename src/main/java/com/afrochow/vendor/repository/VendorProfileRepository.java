package com.afrochow.vendor.repository;

import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorProfileRepository extends JpaRepository<VendorProfile, Long> {

    // ========== FIND BY USER ==========

    Optional<VendorProfile> findByUser(User user);

    Optional<VendorProfile> findByUser_UserId(Long userId);

    Optional<VendorProfile> findByUser_PublicUserId(String publicUserId);

    Optional<VendorProfile> findByPublicVendorId(String publicVendorId);

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

    /**
     * Find all active vendors (filtering by open status must be done in-memory)
     * Note: isOpenNow() is a @Transient method that cannot be used in queries
     */
    @Query("SELECT v FROM VendorProfile v WHERE v.isActive = true")
    List<VendorProfile> findOpenVendors();

    // ========== TOP RATED VENDORS ==========

    @Query("SELECT v FROM VendorProfile v WHERE v.isActive = true ORDER BY SIZE(v.reviews) DESC")
    List<VendorProfile> findTopRatedVendors();

    // ========== FIND BY LOCATION ==========

    @Query("SELECT v FROM VendorProfile v WHERE v.address.city = :city AND v.isActive = true")
    List<VendorProfile> findByCity(@Param("city") String city);

    // ========== COUNTS ==========

    long countByIsActive(boolean active);

    long countByIsActiveTrue();

    long countByIsVerifiedTrueAndIsActiveTrue();

    Long countBy();

    @Query("SELECT v FROM VendorProfile v JOIN FETCH v.user u WHERE u.username = :username")
    Optional<VendorProfile> findByUser_Username(@Param("username") String username);

    Long countByIsVerified(Boolean isVerified);
}