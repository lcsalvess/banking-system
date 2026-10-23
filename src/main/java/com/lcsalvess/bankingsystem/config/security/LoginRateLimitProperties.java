package com.lcsalvess.bankingsystem.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração do limitador de tentativas de login.
 *
 * @param ipCapacity                   capacidade do bucket de requisições por IP
 * @param ipRefillPeriod               período em que o bucket do IP é reabastecido por completo
 * @param maxFailedAttempts            tentativas por combinação de usuário e IP antes do bloqueio
 * @param maxFailedAttemptsPerUsername tentativas por usuário, somando todos os IPs, antes do bloqueio
 * @param failureWindow                janela em que as tentativas são somadas
 * @param lockDuration                 duração do bloqueio
 * @param maximumTrackedKeys           limite de chaves mantidas em memória por cache
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