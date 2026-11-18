package com.lcsalvess.bankingsystem.util.normalization;

/**
 * Provides utility methods for normalizing optional text values.
 */
public final class OptionalTextNormalizer {

  private OptionalTextNormalizer() {
  }

  /**
   * Normalizes optional text by removing surrounding whitespace.
   *
   * <p>Returns {@code null} when the input is null or blank.
   *
   * @param complement the optional text to normalize
   * @return the trimmed text, or {@code null} if the input is null or blank
   */
  public static String normalize(String complement) {
    if (complement == null || complement.isBlank()) {
      return null;
    }

    return complement.trim();
  }
}
