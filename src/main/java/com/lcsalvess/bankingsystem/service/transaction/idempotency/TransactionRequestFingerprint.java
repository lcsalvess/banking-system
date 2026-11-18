package com.lcsalvess.bankingsystem.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDto;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Generates deterministic fingerprints for financial transaction requests.
 *
 * <p>Uses SHA-256 to identify request payloads and distinguish operations
 * with different transaction types, account details, or amounts.
 */
public final class TransactionRequestFingerprint {

  private TransactionRequestFingerprint() {
  }

  /**
   * Generates a fingerprint for a deposit request.
   *
   * <p>Includes the operation type, account number, account digit,
   * and normalized transaction amount.
   *
   * @param request the deposit request to fingerprint
   * @return the hexadecimal SHA-256 fingerprint of the request
   */
  public static String forDeposit(AccountOperationRequestDto request) {
    return hash(
        "DEPOSIT",
        request.accountNumber(),
        request.digit(),
        normalizeAmount(request.amount())
    );
  }

  /**
   * Generates a fingerprint for a withdrawal request.
   *
   * <p>Includes the operation type, account number, account digit,
   * and normalized transaction amount.
   *
   * @param request the withdrawal request to fingerprint
   * @return the hexadecimal SHA-256 fingerprint of the request
   */
  public static String forWithdrawal(AccountOperationRequestDto request) {
    return hash(
        "WITHDRAWAL",
        request.accountNumber(),
        request.digit(),
        normalizeAmount(request.amount())
    );
  }

  /**
   * Generates a fingerprint for a transfer request.
   *
   * <p>Includes the operation type, source and destination account details,
   * and normalized transaction amount.
   *
   * @param request the transfer request to fingerprint
   * @return the hexadecimal SHA-256 fingerprint of the request
   */
  public static String forTransfer(TransferRequestDto request) {
    return hash(
        "TRANSFER",
        request.fromAccountNumber(),
        request.fromAccountDigit(),
        request.toAccountNumber(),
        request.toAccountDigit(),
        normalizeAmount(request.amount())
    );
  }

  /**
   * Strips trailing zeros so that 10, 10.0, and 10.00 produce the same hash,
   * without throwing an exception for values with a scale greater than 2
   * (amount validation is the responsibility of the business layer).
   */
  private static String normalizeAmount(BigDecimal amount) {
    return amount
        .stripTrailingZeros()
        .toPlainString();
  }

  private static String hash(String... components) {
    StringBuilder canonicalRequest = new StringBuilder();

    for (String component : components) {
      canonicalRequest
          .append(component.length())
          .append(':')
          .append(component);
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");

      byte[] hash = digest.digest(
          canonicalRequest.toString()
              .getBytes(StandardCharsets.UTF_8)
      );

      return HexFormat.of().formatHex(hash);

    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
          "The SHA-256 algorithm is not available.",
          exception
      );
    }
  }
}
