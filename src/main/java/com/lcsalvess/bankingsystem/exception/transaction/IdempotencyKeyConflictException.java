package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.springframework.http.HttpStatus;

/**
 * Signals a conflict when an idempotency key is reused for a different request.
 *
 * <p>Represents an HTTP 409 Conflict response.
 */
public class IdempotencyKeyConflictException extends BusinessException {

  public IdempotencyKeyConflictException() {
    super(
        ApiErrorMessages.IDEMPOTENCY_KEY_CONFLICT,
        HttpStatus.CONFLICT
    );
  }
}
