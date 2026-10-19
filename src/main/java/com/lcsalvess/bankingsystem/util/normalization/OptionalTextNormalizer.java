
package com.lcsalvess.bankingsystem.util.normalization;

public final class OptionalTextNormalizer {

    private OptionalTextNormalizer() {
    }

    public static String normalize(String complement) {
        if (complement == null || complement.isBlank()) {
            return null;
        }

        return complement.trim();
    }
}
