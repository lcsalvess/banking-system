package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Indicates that an operation was attempted on an account that is not active.
 */
public class AccountIsNotActiveException extends BusinessException {

  public AccountIsNotActiveException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
