package com.lcsalvess.bankingsystem.unit.exception.security;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.security.RateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.RateLimitOperation;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RateLimitExceededException")
class RateLimitExceededExceptionTests {

    @Test
    @DisplayName("Deve usar a mensagem de login para a operação LOGIN")
    void shouldUseLoginMessageForLoginOperation() {
        RateLimitExceededException exception = new RateLimitExceededException(
                30L,
                RateLimitOperation.LOGIN,
                RateLimitScope.USERNAME_AND_IP
        );

        assertEquals(
                ApiErrorMessages.LOGIN_RATE_LIMIT_EXCEEDED,
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Deve usar a mensagem de troca de senha para a operação PASSWORD_CHANGE")
    void shouldUsePasswordChangeMessageForPasswordChangeOperation() {
        RateLimitExceededException exception = new RateLimitExceededException(
                30L,
                RateLimitOperation.PASSWORD_CHANGE,
                RateLimitScope.USER_ID
        );

        assertEquals(
                ApiErrorMessages.PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED,
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Deve expor a operação, o escopo e o tempo de espera informados")
    void shouldExposeOperationScopeAndRetryAfter() {
        RateLimitExceededException exception = new RateLimitExceededException(
                42L,
                RateLimitOperation.LOGIN,
                RateLimitScope.CLIENT_IP
        );

        assertEquals(42L, exception.getRetryAfterSeconds());
        assertEquals(RateLimitOperation.LOGIN, exception.getOperation());
        assertEquals(RateLimitScope.CLIENT_IP, exception.getScope());
    }

    @ParameterizedTest(name = "espera informada: {0}")
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("Deve informar ao menos 1 segundo quando a espera não é positiva")
    void shouldReturnAtLeastOneSecondWhenWaitIsNotPositive(long retryAfterSeconds) {
        RateLimitExceededException exception = new RateLimitExceededException(
                retryAfterSeconds,
                RateLimitOperation.LOGIN,
                RateLimitScope.CLIENT_IP
        );

        assertEquals(1L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Deve rejeitar operação nula")
    void shouldRejectNullOperation() {
        assertThrows(
                NullPointerException.class,
                () -> new RateLimitExceededException(
                        1L,
                        null,
                        RateLimitScope.CLIENT_IP
                )
        );
    }

    @Test
    @DisplayName("Deve rejeitar escopo nulo")
    void shouldRejectNullScope() {
        assertThrows(
                NullPointerException.class,
                () -> new RateLimitExceededException(
                        1L,
                        RateLimitOperation.LOGIN,
                        null
                )
        );
    }
}