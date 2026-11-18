package com.lcsalvess.bankingsystem.exception.client;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a client is registered with a CPF that already exists.
 */
public class ClientCpfAlreadyExistsException extends BusinessException {

  public ClientCpfAlreadyExistsException(String message) {
    super(message, HttpStatus.CONFLICT);
  }

  public ClientCpfAlreadyExistsException(
      String message,
      Throwable cause
  ) {
    super(message, HttpStatus.CONFLICT, cause);
  }
}
