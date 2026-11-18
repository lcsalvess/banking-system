package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a client attempts to create an account of a type
 * for which they already have an active account.
 */
public class AccountAlreadyExistsException extends BusinessException {

  /**
   * Creates an exception indicating that the client already has an active
   * account of the requested type.
   *
   * @param message the description of the business rule violation
   */
  public AccountAlreadyExistsException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
