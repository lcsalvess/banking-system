package com.lucas.bankingsystem.event.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ClientEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClientEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleClientOperation(ClientOperationEvent event) {
        log.info("Client operation {} successfully completed: id={}, user={}",
                event.type(),
                event.clientId(),
                event.username());
    }
}
