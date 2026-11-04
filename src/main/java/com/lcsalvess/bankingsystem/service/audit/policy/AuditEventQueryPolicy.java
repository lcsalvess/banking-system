package com.lcsalvess.bankingsystem.service.audit.policy;

public final class AuditEventQueryPolicy {

    /**
     * Maximum inclusive date range accepted for audit event queries.
     */
    public static final int MAX_RANGE_DAYS = 366;

    /**
     * Largest page size accepted by audit event queries.
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Maximum number of unexpired snapshots kept for each auditor. Creating
     * another snapshot discards the oldest ones.
     */
    public static final int MAX_ACTIVE_SNAPSHOTS_PER_ACTOR = 5;

    private AuditEventQueryPolicy() {
    }
}
