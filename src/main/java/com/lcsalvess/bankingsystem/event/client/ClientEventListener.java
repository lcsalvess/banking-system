package com.lcsalvess.bankingsystem.event.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for client operation events and logs their successful completion.
 */
@Component
public class ClientEventListener {

  private static final Logger log =
      LoggerFactory.getLogger(ClientEventListener.class);

  /**
   * Logs a client operation after its associated transaction has committed.
   *
   * @param event the event containing the client operation details
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleClientOperation(ClientOperationEvent event) {
    log.info(
        "Client operation {} successfully completed: id={}, user={}",
        event.type(),
        event.clientId(),
        event.username()
    );
  }
}
