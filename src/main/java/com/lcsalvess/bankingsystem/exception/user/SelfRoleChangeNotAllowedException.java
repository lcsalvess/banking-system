package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that a user is not allowed to change their own role.
 *
 * <p>Represents an HTTP 403 Forbidden response.
 */
public class SelfRoleChangeNotAllowedException extends BusinessException {

  public SelfRoleChangeNotAllowedException(String message) {
    super(message, HttpStatus.FORBIDDEN);
  }
}
