package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a transaction would exceed the permitted account balance limit.
 */
public class BalanceLimitExceededException extends BusinessException {

  public BalanceLimitExceededException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
