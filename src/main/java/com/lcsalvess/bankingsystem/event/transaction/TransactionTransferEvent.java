package com.lcsalvess.bankingsystem.event.transaction;

import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents an event describing a completed account transfer.
 *
 * <p>Contains the transfer details required for post-commit event
 * processing and logging.
 *
 * @param type the type of transfer operation
 * @param fromAccountNumber the source account number
 * @param toAccountNumber the destination account number
 * @param amount the transfer amount
 * @param username the username of the user who performed the transfer
 * @param transferCode the unique identifier shared by both transfer transactions
 */
public record TransactionTransferEvent(
    TransactionType type,
    String fromAccountNumber,
    String toAccountNumber,
    BigDecimal amount,
    String username,
    UUID transferCode
) {
}
