package com.lcsalvess.bankingsystem.entity.user;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Preenche created_at e updated_at do {@link User} com o {@link Clock}
 * da aplicação, o mesmo usado nas transações e na auditoria, o que torna
 * os timestamps determinísticos nos testes.
 *
 * <p>O Hibernate instancia este listener pelo contêiner do Spring, por isso
 * o Clock é injetado por construtor. O valor é truncado para microssegundos
 * para ser idêntico ao persistido em TIMESTAMP(6).
 */
@Component
public class UserTimestampListener {

    private final Clock clock;

    public UserTimestampListener(Clock clock) {
        this.clock = clock;
    }

    @PrePersist
    public void onCreate(User user) {
        user.initializeTimestamps(now());
    }

    @PreUpdate
    public void onUpdate(User user) {
        user.refreshUpdatedAt(now());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
