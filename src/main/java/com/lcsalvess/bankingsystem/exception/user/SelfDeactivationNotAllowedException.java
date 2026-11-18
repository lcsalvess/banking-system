package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that an administrator is not allowed to deactivate their own account.
 *
 * <p>Represents an HTTP 403 Forbidden response.
 */
public class SelfDeactivationNotAllowedException extends BusinessException {

  public SelfDeactivationNotAllowedException(String message) {
    super(message, HttpStatus.FORBIDDEN);
  }
}
