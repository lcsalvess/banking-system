package com.lcsalvess.bankingsystem.exception.validation.daterange;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that a date range is invalid.
 *
 * <p>Represents an HTTP 400 Bad Request response.
 */
public class InvalidDateRangeException extends BusinessException {
  public InvalidDateRangeException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
