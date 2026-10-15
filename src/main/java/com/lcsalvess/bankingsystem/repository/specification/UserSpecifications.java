package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
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
                pattern == null
                        ? cb.conjunction()
                        : cb.or(
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
                role == null
                        ? cb.conjunction()
                        : cb.equal(root.get("role"), role),
                active == null
                        ? cb.conjunction()
                        : cb.equal(root.get("active"), active)
        );
    }

    /**
     * Neutraliza os curingas do LIKE para que o termo seja buscado
     * literalmente ("%" e "_" não casam mais "qualquer coisa").
     */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}