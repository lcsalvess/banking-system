package com.lcsalvess.bankingsystem.exception.pagination;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that a requested sorting property is invalid or unsupported.
 *
 * <p>Represents an HTTP 400 Bad Request response.
 */
public class InvalidSortPropertyException extends BusinessException {
  public InvalidSortPropertyException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
