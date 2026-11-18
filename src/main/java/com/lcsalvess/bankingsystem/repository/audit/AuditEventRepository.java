package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import org.springframework.data.repository.Repository;

/**
 * Exposes only write operations for the append-only audit trail.
 * <p>
 * Read and delete operations are intentionally excluded. The database also
 * rejects UPDATE, DELETE, and TRUNCATE operations.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

  /**
   * Saves an audit event to the append-only audit trail.
   *
   * @param auditEvent the audit event to save
   * @return the saved audit event
   */
  AuditEvent save(AuditEvent auditEvent);
}
