package com.lcsalvess.bankingsystem.util.privacy;

/**
 * Provides utilities for masking Brazilian CPF numbers to protect personal information.
 */
public final class CpfMasker {

  private static final String MASKED_CPF = "***.***.***-**";

  private CpfMasker() {
  }

  /**
   * Masks a CPF number, preserving only its last two digits when valid.
   * <p>
   * Non-digit characters are removed before validation. Values that do not
   * contain exactly 11 digits are fully masked.
   *
   * @param cpf the CPF value to mask, or {@code null}
   * @return the masked CPF, or {@code null} when the input is {@code null}
   */
  public static String mask(String cpf) {
    if (cpf == null) {
      return null;
    }

    String digits = cpf.replaceAll("\\D", "");

    if (digits.length() != 11) {
      return MASKED_CPF;
    }

    return "***.***.***-" + digits.substring(9);
  }
}
