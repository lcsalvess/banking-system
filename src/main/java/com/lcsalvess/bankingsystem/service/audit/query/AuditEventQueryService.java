package com.lcsalvess.bankingsystem.service.audit.query;

import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventPageResult;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AuditEventQueryService {

    private final AuditEventQuerySnapshotService snapshotService;

    public AuditEventQueryService(
            AuditEventQuerySnapshotService snapshotService
    ) {
        this.snapshotService = snapshotService;
    }

    public AuditEventPageResult findAll(
            AuditEventQueryFilter filter,
            Pageable pageable,
            String snapshotToken
    ) {
        if (snapshotToken == null) {
            return snapshotService.createSnapshot(filter, pageable);
        }

        return snapshotService.findPage(
                filter,
                pageable,
                snapshotToken
        );
    }
}
