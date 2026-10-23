package com.lcsalvess.bankingsystem.exception.security;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;

import java.util.Objects;

public final class LoginRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;
    private final LoginRateLimitReason reason;

    public LoginRateLimitExceededException(
            long retryAfterSeconds,
            LoginRateLimitReason reason
    ) {
        super(ApiErrorMessages.LOGIN_RATE_LIMIT_EXCEEDED);

        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public LoginRateLimitReason getReason() {
        return reason;
    }
}