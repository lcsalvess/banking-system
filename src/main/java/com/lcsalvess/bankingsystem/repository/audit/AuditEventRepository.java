package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import org.springframework.data.repository.Repository;

/**
 * Só expõe a gravação: a trilha é append-only, então não há busca nem remoção
 * por aqui (o banco também rejeita UPDATE, DELETE e TRUNCATE).
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent auditEvent);
}
