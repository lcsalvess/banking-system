package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that an account does not have sufficient funds to complete a transaction.
 *
 * <p>Represents an HTTP 409 Conflict response.
 */
public class InsufficientBalanceException extends BusinessException {
  public InsufficientBalanceException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
