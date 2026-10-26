package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

public interface AuditEventQueryRepository extends
        Repository<AuditEvent, Long>,
        JpaSpecificationExecutor<AuditEvent> {
}