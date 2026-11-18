package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that a transaction amount is invalid.
 *
 * <p>Represents an HTTP 400 Bad Request response.
 */
public class InvalidAmountException extends BusinessException {
  public InvalidAmountException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
