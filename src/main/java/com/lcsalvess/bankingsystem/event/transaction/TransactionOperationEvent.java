package com.lcsalvess.bankingsystem.event.transaction;

import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents an event describing a completed account transaction.
 *
 * <p>Contains the transaction details required for post-commit event
 * processing and logging.
 *
 * @param type the type of transaction performed
 * @param accountNumber the account number associated with the transaction
 * @param amount the transaction amount
 * @param username the username of the user who performed the transaction
 * @param transactionCode the unique identifier of the transaction
 */
public record TransactionOperationEvent(
    TransactionType type,
    String accountNumber,
    BigDecimal amount,
    String username,
    UUID transactionCode
) {
}
