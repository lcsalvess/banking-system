package com.lcsalvess.bankingsystem.util.normalization;

import java.util.Locale;

/**
 * Provides utility methods for normalizing usernames.
 */
public final class UsernameNormalizer {

  private UsernameNormalizer() {
  }

  /**
   * Normalizes a username by removing surrounding whitespace
   * and converting it to lowercase using {@link Locale#ROOT}.
   *
   * @param username the username to normalize
   * @return the normalized username, or {@code null} if the input is null
   */
  public static String normalize(String username) {
    return username == null
        ? null
        : username.trim().toLowerCase(Locale.ROOT);
  }
}
