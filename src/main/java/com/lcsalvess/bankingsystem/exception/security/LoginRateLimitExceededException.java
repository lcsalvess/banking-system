package com.lcsalvess.bankingsystem.exception.security;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;

public final class LoginRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitExceededException(long retryAfterSeconds) {
        super(ApiErrorMessages.LOGIN_RATE_LIMIT_EXCEEDED);

        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}