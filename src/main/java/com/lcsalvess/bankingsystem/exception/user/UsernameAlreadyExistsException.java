package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that the username is already registered by another user.
 *
 * <p>Represents an HTTP 409 Conflict response.
 */
public class UsernameAlreadyExistsException extends BusinessException {
  public UsernameAlreadyExistsException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
