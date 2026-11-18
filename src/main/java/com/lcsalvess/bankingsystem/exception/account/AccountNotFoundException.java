package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Indicates that the requested account could not be found.
 */
public class AccountNotFoundException extends BusinessException {

  public AccountNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
