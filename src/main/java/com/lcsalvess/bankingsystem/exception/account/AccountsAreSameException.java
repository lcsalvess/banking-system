package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Indicates that a transfer was requested between the same source and destination account.
 */
public class AccountsAreSameException extends BusinessException {

  public AccountsAreSameException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
