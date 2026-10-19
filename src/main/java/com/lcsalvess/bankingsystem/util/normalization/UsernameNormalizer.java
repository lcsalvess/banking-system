package com.lcsalvess.bankingsystem.util.normalization;

import java.util.Locale;

public final class UsernameNormalizer {

    private UsernameNormalizer() {
    }

    public static String normalize(String username) {
        return username == null
                ? null
                : username.trim().toLowerCase(Locale.ROOT);
    }
}
