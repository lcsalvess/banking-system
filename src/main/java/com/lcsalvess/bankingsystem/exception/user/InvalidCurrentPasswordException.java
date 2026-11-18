package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that the current password provided during a password change is invalid.
 *
 * <p>Represents an HTTP 400 Bad Request response.
 */
public class InvalidCurrentPasswordException extends BusinessException {

  public InvalidCurrentPasswordException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
