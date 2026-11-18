package com.lcsalvess.bankingsystem.exception.base;

import org.springframework.http.HttpStatus;

/**
 * Base exception for business rule violations with an associated HTTP status.
 */
public abstract class BusinessException extends RuntimeException {

  private final HttpStatus status;

  protected BusinessException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }

  protected BusinessException(
      String message,
      HttpStatus status,
      Throwable cause
  ) {
    super(message, cause);
    this.status = status;
  }

  /**
   * Returns the HTTP status associated with this business exception.
   *
   * @return the HTTP status
   */
  public HttpStatus getStatus() {
    return status;
  }
}
