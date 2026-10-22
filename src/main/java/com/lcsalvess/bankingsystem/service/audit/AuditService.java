package com.lcsalvess.bankingsystem.service.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AuditService(
            AuditEventRepository auditEventRepository,
            CurrentUserService currentUserService,
            Clock clock
    ) {
        this.auditEventRepository = auditEventRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    /**
     * Registra uma operação bem-sucedida na MESMA transação da alteração
     * auditada. Com MANDATORY, chamar fora de uma transação falha de forma
     * explícita, e uma falha na gravação desfaz a operação inteira: nunca
     * existe uma operação commitada sem o seu registro.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(
            AuditEventType eventType,
            AuditEntityType entityType,
            String entityId
    ) {
        Optional<User> actor = currentUserService.findCurrentUser();

        auditEventRepository.save(
                new AuditEvent(
                        eventType,
                        AuditOutcome.SUCCESS,
                        actor.isPresent()
                                ? AuditActorType.USER
                                : AuditActorType.ANONYMOUS,
                        actor.map(User::getId).orElse(null),
                        actor.map(User::getUsername).orElse(null),
                        entityType,
                        entityId,
                        currentCorrelationId(),
                        LocalDateTime.now(clock)
                )
        );
    }

    private UUID currentCorrelationId() {
        String correlationId =
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

        if (correlationId == null) {
            return null;
        }

        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
