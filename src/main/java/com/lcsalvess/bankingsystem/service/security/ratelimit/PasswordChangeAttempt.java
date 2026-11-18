package com.lcsalvess.bankingsystem.service.security.ratelimit;

import java.time.Instant;

/**
 * Password change attempt accepted by {@link RateLimitProtectionService}.
 *
 * <p>The attempt has already been counted when this object is created.
 * It is used to reverse the count ({@code cancelPasswordChangeAttempt})
 * or reset it after successful current password verification
 * ({@code recordSuccessfulCurrentPasswordVerification}).
 *
 * <p>The window start prevents cancellation from affecting a newer sequence.
 */
public record PasswordChangeAttempt(
    String key,
    Instant windowStartedAt
) {
}
