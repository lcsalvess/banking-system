package com.lcsalvess.bankingsystem.event.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles user-related events after their associated database transactions commit.
 *
 * <p>Logs successfully completed user operations and role changes.
 */
@Component
public class UserEventListener {

  private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

  /**
   * Logs a successfully completed user operation.
   *
   * @param event the event containing the user identifier and operation type
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserOperation(UserOperationEvent event) {
    log.info("User operation {} successfully completed: id={}",
        event.type(),
        event.userId());
  }

  /**
   * Logs a successfully completed user role change.
   *
   * @param event the event containing the user identifier and role transition details
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserRoleChange(UserRoleChangedEvent event) {
    log.info("User role changed: id={}, previousRole={}, newRole={}",
        event.userId(),
        event.previousRole(),
        event.newRole());
  }
}
