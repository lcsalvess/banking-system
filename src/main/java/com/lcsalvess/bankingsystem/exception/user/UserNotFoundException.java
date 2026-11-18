package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that the requested user could not be found.
 *
 * <p>Represents an HTTP 404 Not Found response.
 */
public class UserNotFoundException extends BusinessException {

  public UserNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
