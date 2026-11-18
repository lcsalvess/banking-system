package com.lcsalvess.bankingsystem.event.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles transaction events after their associated database transactions commit.
 *
 * <p>Logs successfully completed deposit, withdrawal, and transfer operations.
 */
@Component
public class TransactionEventListener {

  private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

  /**
   * Logs a successfully completed account transaction.
   *
   * @param event the event containing the transaction details
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleTransactionOperation(TransactionOperationEvent event) {
    log.info("{} successfully processed: account={}, amount={}, user={}",
        event.type(),
        event.accountNumber(),
        event.amount(),
        event.username());
  }

  /**
   * Logs a successfully completed account transfer.
   *
   * @param event the event containing the transfer details
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleTransactionTransferOperation(TransactionTransferEvent event) {
    log.info("{} successfully processed: from={}, to={}, amount={}, user={}",
        event.type(),
        event.fromAccountNumber(),
        event.toAccountNumber(),
        event.amount(),
        event.username());
  }
}
