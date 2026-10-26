package com.lcsalvess.bankingsystem.service.audit;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQueryRepository;
import com.lcsalvess.bankingsystem.repository.specification.AuditEventSpecifications;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import com.lcsalvess.bankingsystem.validation.daterange.QueryDateRangeValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class AuditEventQueryService {

    /**
     * Maior intervalo (inclusivo) aceito quando início e fim são informados.
     */
    public static final int MAX_RANGE_DAYS = 366;

    private final AuditEventQueryRepository auditEventQueryRepository;
    private final Clock clock;

    public AuditEventQueryService(
            AuditEventQueryRepository auditEventQueryRepository,
            Clock clock
    ) {
        this.auditEventQueryRepository = auditEventQueryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponseDTO> findAll(
            LocalDate startDate,
            LocalDate endDate,
            AuditEventType eventType,
            AuditOutcome outcome,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId,
            Pageable pageable
    ) {
        QueryDateRangeValidator.validate(
                startDate,
                endDate,
                clock,
                MAX_RANGE_DAYS
        );

        return auditEventQueryRepository.findAll(
                        AuditEventSpecifications.byFilters(
                                startDate,
                                endDate,
                                eventType,
                                outcome,
                                actorUserId,
                                actorUsername,
                                entityType,
                                entityId
                        ),
                        PageableTieBreaker.withIdTieBreaker(pageable)
                )
                .map(AuditEventResponseDTO::fromEntity);
    }
}