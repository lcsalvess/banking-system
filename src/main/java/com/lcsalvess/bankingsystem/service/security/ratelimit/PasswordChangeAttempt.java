package com.lcsalvess.bankingsystem.service.security.ratelimit;

import java.time.Instant;

/**
 * Tentativa de troca de senha admitida pelo {@link RateLimitProtectionService}.
 *
 * <p>A tentativa já está contabilizada quando este objeto é criado. Ele serve
 * para desfazer a contagem ({@code cancelPasswordChangeAttempt}) ou zerá-la
 * após a confirmação da senha atual ({@code recordSuccessfulCurrentPasswordVerification}).
 * O início da janela impede que o cancelamento altere uma sequência mais nova.
 */
public record PasswordChangeAttempt(
        String key,
        Instant windowStartedAt
) {
}