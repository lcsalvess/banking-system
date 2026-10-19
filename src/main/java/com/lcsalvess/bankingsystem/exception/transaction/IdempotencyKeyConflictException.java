package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends BusinessException {

    public IdempotencyKeyConflictException() {
        super(
                ApiErrorMessages.IDEMPOTENCY_KEY_CONFLICT,
                HttpStatus.CONFLICT
        );
    }
}