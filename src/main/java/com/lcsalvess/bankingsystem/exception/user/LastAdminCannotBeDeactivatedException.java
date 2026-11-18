package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that an administrator cannot be deactivated because they are
 * the last active administrator in the system.
 *
 * <p>Represents an HTTP 409 Conflict response.
 */
public class LastAdminCannotBeDeactivatedException extends BusinessException {

  public LastAdminCannotBeDeactivatedException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
