package com.lcsalvess.bankingsystem.integration.address.exception;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that the requested postal code could not be found.
 *
 * <p>Represents an HTTP 404 Not Found response.
 */
public class PostalCodeNotFoundException extends BusinessException {

  public PostalCodeNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
