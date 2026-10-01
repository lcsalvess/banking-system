package com.lucas.bankingsystem.event.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionOperation(TransactionOperationEvent event) {
        log.info("{} successfully processed: account={}, amount={}, user={}",
                event.type(),
                event.accountNumber(),
                event.amount(),
                event.username());
    }

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
