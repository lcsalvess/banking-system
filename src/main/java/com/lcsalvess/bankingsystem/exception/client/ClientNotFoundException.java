package com.lcsalvess.bankingsystem.exception.client;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested client cannot be found.
 */
public class ClientNotFoundException extends BusinessException {

  public ClientNotFoundException() {
    super(ApiErrorMessages.CLIENT_NOT_FOUND, HttpStatus.NOT_FOUND);
  }

  public ClientNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
