package com.afrochow.user.repository;

import com.afrochow.common.enums.Role;
import com.afrochow.user.model.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Reusable {@link Specification} factories for {@link User} queries.
 *
 * <p>Each factory returns {@code null} when its argument is null/blank, which
 * makes it safe to chain with {@link Specification#where(Specification)} and
 * {@code .and(...)} regardless of which filters are active. The repository
 * treats a {@code null} spec as "no constraint", so optional filters compose
 * cleanly without if/else ladders.
 */
public final class UserSpecifications {

    private UserSpecifications() {}

    /** Restricts to a single role. No-op when {@code role} is null. */
    public static Specification<User> hasRole(Role role) {
        if (role == null) return null;
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /** Restricts by isActive flag. No-op when {@code active} is null. */
    public static Specification<User> isActive(Boolean active) {
        if (active == null) return null;
        return (root, query, cb) -> cb.equal(root.get("isActive"), active);
    }

    /**
     * Case-insensitive substring match against firstName OR lastName.
     * No-op when the term is null/blank or shorter than 2 characters
     * (matches the legacy controller threshold).
     */
    public static Specification<User> nameContains(String term) {
        if (term == null) return null;
        String trimmed = term.trim();
        if (trimmed.length() < 2) return null;
        String like = "%" + trimmed.toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate first = cb.like(cb.lower(root.get("firstName")), like);
            Predicate last  = cb.like(cb.lower(root.get("lastName")),  like);
            return cb.or(first, last);
        };
    }

    /** createdAt &gt;= start. No-op when start is null. */
    public static Specification<User> createdAtAfter(LocalDateTime start) {
        if (start == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    /** createdAt &lt;= end. No-op when end is null. */
    public static Specification<User> createdAtBefore(LocalDateTime end) {
        if (end == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end);
    }
}
