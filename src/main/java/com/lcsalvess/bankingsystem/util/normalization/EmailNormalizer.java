package com.lcsalvess.bankingsystem.util.normalization;

import java.util.Locale;

/**
 * Provides utility methods for normalizing email addresses.
 */
public final class EmailNormalizer {

  private EmailNormalizer() {
  }

  /**
   * Normalizes an email address by removing surrounding whitespace
   * and converting it to lowercase using {@link Locale#ROOT}.
   *
   * @param email the email address to normalize
   * @return the normalized email address, or {@code null} if the input is null
   */
  public static String normalize(String email) {
    return email == null
        ? null
        : email.trim().toLowerCase(Locale.ROOT);
  }
}
