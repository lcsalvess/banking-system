package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import org.springframework.data.repository.Repository;

/**
 * Only exposes write operations: the audit trail is append-only, so no read
 * or delete operations are provided here (the database also rejects UPDATE,
 * DELETE, and TRUNCATE operations).
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent auditEvent);
}
