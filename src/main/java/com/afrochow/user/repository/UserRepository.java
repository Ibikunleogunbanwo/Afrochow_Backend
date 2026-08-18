package com.afrochow.user.repository;

import com.afrochow.common.enums.Role;
import com.afrochow.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // Lookups
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByPhone(String phone);

    Boolean existsByPhone(String phone);

    Optional<User> findByPublicUserId(String publicUserId);

    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    // Existence checks
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPublicUserId(String publicUserId);

    // Role and status filters
    List<User> findByRole(Role role);

    List<User> findAllByRole(Role role);

    Page<User> findAllByRole(Role role, Pageable pageable);

    List<User> findByRoleAndIsActive(Role role, Boolean isActive);

    List<User> findByIsActive(Boolean isActive);

    Page<User> findByIsActive(Boolean isActive, Pageable pageable);

    Page<User> findByRoleAndIsActive(Role role, Boolean isActive, Pageable pageable);

    List<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName);

    // Account deletion cleanup
    List<User> findByScheduledForDeletionAtBefore(LocalDateTime cutoff);

    // Counts
    Long countByIsActiveTrue();

    Long countByIsActiveFalse();

    Long countByRole(Role role);

    Long countByRoleAndIsActiveTrue(Role role);

    Long countByRoleAndEmailVerifiedTrue(Role role);

    /**
     * Counts genuinely registered accounts, excluding the demo/showroom data the app
     * ships with. Total user count is misleading at launch because the seeded
     * catalogue contributes hundreds of vendor and customer rows — this is the number
     * that actually reflects sign-up activity.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isSeedData = false")
    Long countRealUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.isSeedData = false AND u.role = :role")
    Long countRealUsersByRole(@Param("role") Role role);

    /**
     * Counts users whose {@code createdAt} falls in the inclusive range
     * [{@code start}, {@code end}]. Used by the admin dashboard so the
     * "New Users" card reflects the whole table, not just the current page.
     */
    @Query("SELECT COUNT(u) FROM User u " +
           "WHERE u.createdAt >= :start AND u.createdAt <= :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start,
                                 @Param("end")   LocalDateTime end);

    @Query("SELECT COUNT(u) FROM User u " +
           "WHERE u.role = :role " +
           "  AND u.createdAt >= :start AND u.createdAt <= :end")
    long countByRoleAndCreatedAtBetween(@Param("role") Role role,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
