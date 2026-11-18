package com.lcsalvess.bankingsystem.event.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for account operation events and records their successful completion.
 */
@Component
public class AccountEventListener {

  private static final Logger log = LoggerFactory.getLogger(AccountEventListener.class);

  /**
   * Handles account operation events after the associated transaction commits.
   *
   * @param event the account operation event containing operation details
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleAccountOperation(AccountOperationEvent event) {
    log.info(
        "Account operation {} successfully completed: id={}, user={}",
        event.type(),
        event.accountId(),
        event.username()
    );
  }
}
