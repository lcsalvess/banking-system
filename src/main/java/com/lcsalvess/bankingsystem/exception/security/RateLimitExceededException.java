package com.lcsalvess.bankingsystem.exception.security;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;

import java.util.Objects;

/**
 * Indicates that an operation has exceeded its configured rate limit.
 */
public final class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;
    private final RateLimitOperation operation;
    private final RateLimitScope scope;

    public RateLimitExceededException(
            long retryAfterSeconds,
            RateLimitOperation operation,
            RateLimitScope scope
    ) {
        super(messageFor(operation));

        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
        this.operation = Objects.requireNonNull(operation, "operation");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    private static String messageFor(RateLimitOperation operation) {
        return switch (Objects.requireNonNull(operation, "operation")) {
            case LOGIN -> ApiErrorMessages.LOGIN_RATE_LIMIT_EXCEEDED;
            case PASSWORD_CHANGE ->
                    ApiErrorMessages.PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED;
        };
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public RateLimitOperation getOperation() {
        return operation;
    }

    public RateLimitScope getScope() {
        return scope;
    }
}