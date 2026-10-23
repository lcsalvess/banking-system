package com.lcsalvess.bankingsystem.service.security;

import java.time.Instant;

/**
 * Tentativa de login admitida pelo {@link LoginAttemptProtectionService}.
 *
 * <p>A tentativa já está contabilizada quando este objeto é criado. Ele serve
 * para desfazer a contagem ({@code cancelAttempt}) ou zerá-la após um login
 * bem-sucedido ({@code recordSuccessfulLogin}). O início da janela de cada
 * contador impede que o cancelamento altere uma sequência mais nova.
 */
public record LoginAttempt(
        String usernameAndIpKey,
        Instant usernameAndIpWindowStartedAt,
        String usernameKey,
        Instant usernameWindowStartedAt
) {
}
