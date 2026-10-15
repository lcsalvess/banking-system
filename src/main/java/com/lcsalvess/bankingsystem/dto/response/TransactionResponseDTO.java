package com.lcsalvess.bankingsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lcsalvess.bankingsystem.serialization.MoneySerializer;
import com.lcsalvess.bankingsystem.entity.Transaction;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
                example = "2026-10-15T14:30:00"
        )
        LocalDateTime createdAt
) {
    public static TransactionResponseDTO fromEntity(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getTransactionCode(),
                transaction.getTransferCode(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt()
        );
    }
}
