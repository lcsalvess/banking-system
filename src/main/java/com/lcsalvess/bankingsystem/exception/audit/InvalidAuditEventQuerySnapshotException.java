package com.lcsalvess.bankingsystem.exception.audit;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Signals that an audit event query snapshot is invalid.
 *
 * <p>Represents an HTTP 400 Bad Request response.
 */
public class InvalidAuditEventQuerySnapshotException extends BusinessException {

  public InvalidAuditEventQuerySnapshotException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
