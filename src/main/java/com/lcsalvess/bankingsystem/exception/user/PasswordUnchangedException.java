package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that a password change was attempted using the current password
 * as the new password.
 *
 * <p>Represents an HTTP 400 Bad Request response.
 */
public class PasswordUnchangedException extends BusinessException {

  public PasswordUnchangedException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
