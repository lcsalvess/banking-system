package com.lcsalvess.bankingsystem.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TransactionRequestFingerprint {

    private TransactionRequestFingerprint() {
    }

    public static String forDeposit(AccountOperationRequestDTO request) {
        return hash(
                "DEPOSIT",
                request.accountNumber(),
                request.digit(),
                normalizeAmount(request.amount())
        );
    }

    public static String forWithdrawal(AccountOperationRequestDTO request) {
        return hash(
                "WITHDRAWAL",
                request.accountNumber(),
                request.digit(),
                normalizeAmount(request.amount())
        );
    }

    public static String forTransfer(TransferRequestDTO request) {
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
