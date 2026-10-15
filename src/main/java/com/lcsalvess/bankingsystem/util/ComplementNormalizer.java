
package com.lcsalvess.bankingsystem.util;

public final class ComplementNormalizer {

    private ComplementNormalizer() {
    }

    public static String normalize(String complement) {
        if (complement == null || complement.isBlank()) {
            return null;
        }

        return complement.trim();
    }
}
