package com.afrochow.user.repository;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository
        extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {


    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByPhone(String phone);

    Boolean existsByPhone(String phone);

    Optional<User> findByPublicUserId(String publicUserId);


    List<User> findByRole(Role role);

    List<User> findAllByRole(Role role);

    Page<User> findAllByRole(Role role, Pageable pageable);

    List<User> findByRoleAndIsActive(Role role, Boolean isActive);


    List<User> findByIsActive(Boolean isActive);

    Page<User> findByIsActive(Boolean isActive, Pageable pageable);

    Page<User> findByRoleAndIsActive(Role role, Boolean isActive, Pageable pageable);

    List<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPublicUserId(String publicUserId);

    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    // ========== DELETION CLEANUP ==========

    List<User> findByScheduledForDeletionAtBefore(LocalDateTime cutoff);

    // ========== COUNT METHODS ==========

    Long countByIsActiveTrue();

    Long countByIsActiveFalse();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    Long countByRole(@Param("role") Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.isActive = true")
    Long countByRoleAndIsActiveTrue(@Param("role") Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.emailVerified = true")
    Long countByRoleAndEmailVerifiedTrue(@Param("role") Role role);

    // ========== DATE-RANGE COUNTS (used by admin dashboard) ==========

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
    long countByRoleAndCreatedAtBetween(@Param("role")  Role role,
                                        @Param("start") LocalDateTime start,
                                        @Param("end")   LocalDateTime end);
}