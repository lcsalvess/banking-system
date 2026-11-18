package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to cancel an account that still has
 * a remaining balance.
 */
public class AccountHasBalanceException extends BusinessException {

  /**
   * Creates an exception indicating that the account cannot be canceled
   * while its balance is non-zero.
   *
   * @param message the description of the business rule violation
   */
  public AccountHasBalanceException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
