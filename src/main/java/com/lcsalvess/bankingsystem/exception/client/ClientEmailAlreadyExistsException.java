package com.lcsalvess.bankingsystem.exception.client;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a client is registered with an email address that already exists.
 */
public class ClientEmailAlreadyExistsException extends BusinessException {

  public ClientEmailAlreadyExistsException(String message) {
    super(message, HttpStatus.CONFLICT);
  }

  public ClientEmailAlreadyExistsException(String message, Throwable cause) {
    super(message, HttpStatus.CONFLICT, cause);
  }
}
