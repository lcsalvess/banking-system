
package com.lcsalvess.bankingsystem.util;

public final class NameNormalizer {

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        return name == null ? null : name.trim();
    }
}
