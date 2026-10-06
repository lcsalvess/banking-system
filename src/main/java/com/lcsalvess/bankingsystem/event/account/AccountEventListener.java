package com.lcsalvess.bankingsystem.event.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AccountEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAccountOperation(AccountOperationEvent event) {
        log.info("Account operation {} successfully completed: id={}, user={}",
                event.type(),
                event.accountId(),
                event.username());
    }
}
