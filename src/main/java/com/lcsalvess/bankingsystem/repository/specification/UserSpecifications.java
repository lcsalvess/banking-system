package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.entity.user.UserType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    public static Specification<User> byFilters(
            String search,
            Role role,
            Boolean active
    ) {
        String pattern = search == null
                ? null
                : "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";

        return (root, query, cb) -> cb.and(

                // Only human users are included in the ordinary user listing.
                cb.equal(root.get("userType"), UserType.HUMAN),

                // Search by employee ID, full name, username or email.
                pattern == null
                        ? cb.conjunction()
                        : cb.or(
                        cb.like(
                                cb.lower(root.get("employeeId")),
                                pattern,
                                LIKE_ESCAPE
                        ),
                        cb.like(
                                cb.lower(root.get("fullName")),
                                pattern,
                                LIKE_ESCAPE
                        ),
                        cb.like(
                                cb.lower(root.get("username")),
                                pattern,
                                LIKE_ESCAPE
                        ),
                        cb.like(
                                cb.lower(root.get("email")),
                                pattern,
                                LIKE_ESCAPE
                        )
                ),

                // Optional role filter.
                role == null
                        ? cb.conjunction()
                        : cb.equal(root.get("role"), role),

                // Optional account status filter.
                active == null
                        ? cb.conjunction()
                        : cb.equal(root.get("active"), active)
        );
    }

    /**
     * Escapes LIKE wildcard characters so that search terms are matched
     * literally rather than treating "%" and "_" as wildcards.
     */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
