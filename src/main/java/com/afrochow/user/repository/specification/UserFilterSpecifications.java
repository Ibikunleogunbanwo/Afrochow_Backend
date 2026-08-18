package com.afrochow.user.repository.specification;

import com.afrochow.common.enums.Role;
import com.afrochow.user.model.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Reusable filter builders for admin {@link User} queries.
 *
 * <p>Each method creates one optional database filter. When a filter value is
 * missing, the method returns {@code null}; the controller removes those nulls
 * before combining the active filters into one query.
 */
public final class UserFilterSpecifications {

    private UserFilterSpecifications() {}

    /** Restricts to a single role. No-op when {@code role} is null. */
    public static Specification<User> hasRole(Role role) {
        return role == null ? null : (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /** Restricts by isActive flag. No-op when {@code active} is null. */
    public static Specification<User> isActive(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("isActive"), active);
    }

    /**
     * Case-insensitive substring match against firstName OR lastName.
     * No-op when the term is null/blank or shorter than 2 characters
     * (matches the legacy controller threshold).
     */
    public static Specification<User> nameContains(String term) {
        if (!StringUtils.hasText(term)) return null;

        String trimmed = term.trim();
        if (trimmed.length() < 2) return null;

        String like = "%" + trimmed.toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate first = cb.like(cb.lower(root.get("firstName")), like);
            Predicate last = cb.like(cb.lower(root.get("lastName")), like);
            return cb.or(first, last);
        };
    }

    /** createdAt &gt;= start. No-op when start is null. */
    public static Specification<User> createdAtAfter(LocalDateTime start) {
        return start == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    /** createdAt &lt;= end. No-op when end is null. */
    public static Specification<User> createdAtBefore(LocalDateTime end) {
        return end == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end);
    }

    /**
     * Restricts to demo/seed accounts ({@code true}) or genuine registrations
     * ({@code false}). No-op when null.
     *
     * <p>Applied server-side rather than in the browser on purpose: the user
     * list is paginated, so filtering client-side would only hide seeded rows
     * on the page that happened to be loaded and would leave the total count
     * and page numbers reporting the unfiltered set.
     */
    public static Specification<User> isSeedData(Boolean seedData) {
        return seedData == null ? null : (root, query, cb) -> cb.equal(root.get("isSeedData"), seedData);
    }
}
