package com.lcsalvess.bankingsystem.service.security.ratelimit;

import java.time.Instant;

/**
 * Login attempt accepted by {@link RateLimitProtectionService}.
 *
 * <p>The attempt has already been counted when this object is created.
 * It is used to reverse the count ({@code cancelAttempt}) or reset it
 * after a successful login ({@code recordSuccessfulLogin}).
 *
 * <p>The start time of each counter window prevents cancellation from
 * affecting a newer sequence.
 */
public record LoginAttempt(
    String usernameAndIpKey,
    Instant usernameAndIpWindowStartedAt,
    String usernameKey,
    Instant usernameWindowStartedAt
) {
}
