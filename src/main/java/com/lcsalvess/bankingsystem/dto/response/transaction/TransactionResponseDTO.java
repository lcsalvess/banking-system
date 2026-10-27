package com.lcsalvess.bankingsystem.dto.response.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.serialization.MoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponseDTO(
        @Schema(
                description = "Unique public identifier of the transaction.",
                example = "108cf2a1-bcee-460a-a5db-f9b231947411"
        )
        UUID transactionCode,

        @Schema(
                description = "Identifier shared by the transactions associated with a transfer. Null for transactions unrelated to a transfer.",
                example = "a12bc34d-5678-4abc-9def-1234567890ab",
                nullable = true
        )
        UUID transferCode,

        @Schema(
                description = "Type of transaction."
        )
        TransactionType type,

        @Schema(
                description = "Transaction amount, formatted as a monetary value.",
                example = "450.95"
        )
        @JsonSerialize(using = MoneySerializer.class)
        BigDecimal amount,

        @Schema(
                description = "Date and time when the transaction was created.",
                example = "2026-10-15T17:30:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "Reference date used for the yield. Omitted for transactions other than YIELD.",
                example = "2026-10-14",
                nullable = true
        )
        LocalDate yieldDate,

        @Schema(
                description = "Account balance immediately after this transaction was applied.",
                example = "150.00"
        )
        @JsonSerialize(using = MoneySerializer.class)
        BigDecimal balanceAfter
) {
    public static TransactionResponseDTO fromEntity(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getTransactionCode(),
                transaction.getTransferCode(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt(),
                transaction.getYieldDate(),
                transaction.getBalanceAfter()
        );
    }
}
