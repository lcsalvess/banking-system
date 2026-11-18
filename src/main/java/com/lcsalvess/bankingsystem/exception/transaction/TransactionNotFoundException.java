package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that the requested transaction could not be found.
 *
 * <p>Represents an HTTP 404 Not Found response.
 */
public class TransactionNotFoundException extends BusinessException {
  public TransactionNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
