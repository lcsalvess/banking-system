package com.lcsalvess.bankingsystem.util.normalization;

/**
 * Provides utility methods for normalizing names.
 */
public final class NameNormalizer {

  private NameNormalizer() {
  }

  /**
   * Removes leading and trailing whitespace from a name.
   *
   * @param name the name to normalize
   * @return the trimmed name, or {@code null} if the input is null
   */
  public static String normalize(String name) {
    return name == null ? null : name.trim();
  }
}
