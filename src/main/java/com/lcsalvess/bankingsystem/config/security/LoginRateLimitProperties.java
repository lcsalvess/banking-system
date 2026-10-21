package com.lcsalvess.bankingsystem.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "banking.security.login")
public record LoginRateLimitProperties(
        int ipCapacity,
        Duration ipRefillPeriod,
        int maxFailedAttempts,
        Duration failureWindow,
        Duration lockDuration,
        long maximumTrackedKeys
) {

    public LoginRateLimitProperties {
        if (ipCapacity < 1 || maxFailedAttempts < 1 || maximumTrackedKeys < 1) {
            throw new IllegalArgumentException("Login rate-limit capacities must be positive.");
        }

        if (isInvalidDuration(ipRefillPeriod)
                || isInvalidDuration(failureWindow)
                || isInvalidDuration(lockDuration)) {
            throw new IllegalArgumentException("Login rate-limit durations must be positive.");
        }
    }

    private static boolean isInvalidDuration(Duration duration) {
        return duration == null || duration.isNegative() || duration.isZero();
    }
}