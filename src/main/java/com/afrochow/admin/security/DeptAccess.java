package com.afrochow.admin.security;

import com.afrochow.admin.model.AdminProfile;
import com.afrochow.common.enums.AdminArea;
import com.afrochow.common.enums.Department;
import com.afrochow.common.enums.Role;
import com.afrochow.security.Utils.SecurityUtils;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Department-based access control for the admin panel.
 *
 * <p>Referenced from {@code @PreAuthorize("@deptAccess.can('VENDORS')")} on
 * admin controller methods. SUPERADMIN accounts always pass, regardless of
 * their own department. Plain ADMIN accounts pass only if their
 * {@code AdminProfile.department} is listed as having access to the given
 * {@link AdminArea}.
 *
 * <p>This intentionally sits alongside, not instead of, the existing
 * SUPERADMIN-only method overrides (role changes, user/product deletion,
 * Stripe account relinking) — those stay hard-gated to
 * {@code hasRole('SUPERADMIN')} and are untouched by this class, since
 * department scoping only ever narrows what a plain ADMIN can do, never
 * widens it.
 */
@Slf4j
@Component("deptAccess")
@RequiredArgsConstructor
public class DeptAccess {

    private final UserRepository userRepository;

    /**
     * Which departments have access to which admin areas. A department not
     * listed for an area has no access to it. A department with no entries
     * anywhere (e.g. HR) has no admin-panel resource access at all beyond
     * their own profile.
     */
    private static final Map<AdminArea, Set<Department>> AREA_DEPARTMENTS = Map.of(
            AdminArea.USERS,      EnumSet.of(Department.CUSTOMER_SUPPORT),
            AdminArea.VENDORS,    EnumSet.of(Department.OPERATIONS),
            AdminArea.PRODUCTS,   EnumSet.of(Department.OPERATIONS),
            AdminArea.ORDERS,     EnumSet.of(Department.OPERATIONS, Department.CUSTOMER_SUPPORT),
            AdminArea.PAYMENTS,   EnumSet.of(Department.FINANCE),
            AdminArea.REVIEWS,    EnumSet.of(Department.CUSTOMER_SUPPORT),
            AdminArea.PROMOTIONS, EnumSet.of(Department.MARKETING),
            AdminArea.BROADCAST,  EnumSet.of(Department.MARKETING),
            AdminArea.CATEGORIES, EnumSet.of(Department.OPERATIONS, Department.MARKETING),
            AdminArea.REPORTS,    EnumSet.of(Department.FINANCE, Department.MARKETING, Department.MANAGEMENT)
    );

    /**
     * @param areaName name of an {@link AdminArea} constant (passed as a string
     *                 from SpEL, e.g. {@code @deptAccess.can('VENDORS')})
     * @return true if the current user may access that area
     */
    public boolean can(String areaName) {
        AdminArea area;
        try {
            area = AdminArea.valueOf(areaName);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("DeptAccess.can() called with unknown area: {}", areaName);
            return false;
        }

        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return false;
        }

        // Fetch fresh from the DB rather than SecurityUtils.getCurrentUser() —
        // that method returns the User captured from the JWT-backed security
        // context, which can be stale (e.g. right after a promote/demote or a
        // department change, before the user's token is refreshed).
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        if (user.getRole() == Role.SUPERADMIN) {
            return true;
        }
        if (user.getRole() != Role.ADMIN) {
            return false;
        }

        AdminProfile profile = user.getAdminProfile();
        if (profile == null || profile.getDepartment() == null) {
            return false;
        }

        return AREA_DEPARTMENTS.getOrDefault(area, Set.of()).contains(profile.getDepartment());
    }
}
