package com.lcsalvess.bankingsystem.service.transaction.yield;

import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;

/**
 * Final status of one daily yield run. The enum name is stored as the audit
 * reason code (it matches ck_audit_events_reason).
 */
public enum DailyYieldRunStatus {
    COMPLETED(AuditOutcome.SUCCESS),
    COMPLETED_WITH_FAILURES(AuditOutcome.FAILURE),
    ABORTED(AuditOutcome.FAILURE);

    private final AuditOutcome outcome;

    DailyYieldRunStatus(AuditOutcome outcome) {
        this.outcome = outcome;
    }

    public AuditOutcome outcome() {
        return outcome;
    }
}
