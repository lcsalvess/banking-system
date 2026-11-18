package com.lcsalvess.bankingsystem.config.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for login attempt rate limiting.
 *
 * @param ipCapacity                   capacity of the request bucket for each IP address
 * @param ipRefillPeriod               period required to fully refill the IP address bucket
 * @param maxFailedAttempts            maximum failed attempts per username and IP before lockout
 * @param maxFailedAttemptsPerUsername maximum failed attempts per username across all IPs
 *                                     before lockout
 * @param failureWindow                time window over which failed attempts are counted
 * @param lockDuration                 duration of the lockout
 * @param maximumTrackedKeys           maximum number of keys retained in memory by each cache
 */
@ConfigurationProperties(prefix = "banking.security.login")
public record LoginRateLimitProperties(
    int ipCapacity,
    Duration ipRefillPeriod,
    int maxFailedAttempts,
    int maxFailedAttemptsPerUsername,
    Duration failureWindow,
    Duration lockDuration,
    long maximumTrackedKeys
) {

  public LoginRateLimitProperties {
    if (ipCapacity < 1
        || maxFailedAttempts < 1
        || maxFailedAttemptsPerUsername < 1
        || maximumTrackedKeys < 1) {
      throw new IllegalArgumentException("Login rate-limit capacities must be positive.");
    }

    if (maxFailedAttemptsPerUsername < maxFailedAttempts) {
      throw new IllegalArgumentException(
          "The per-username attempt limit must not be lower than the "
              + "per-username-and-IP attempt limit."
      );
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
