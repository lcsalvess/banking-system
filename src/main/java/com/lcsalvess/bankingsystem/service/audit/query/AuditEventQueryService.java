package com.lcsalvess.bankingsystem.service.audit.query;

import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventPageResult;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Coordinates audit event queries using snapshot-based pagination.
 */
@Service
public class AuditEventQueryService {

  private final AuditEventQuerySnapshotService snapshotService;

  public AuditEventQueryService(
      AuditEventQuerySnapshotService snapshotService
  ) {
    this.snapshotService = snapshotService;
  }

  /**
   * Retrieves a page of audit events using a new or existing snapshot.
   *
   * @param filter the filters applied to the audit event query
   * @param pageable the pagination and sorting configuration
   * @param snapshotToken the token identifying an existing snapshot, or {@code null}
   *                      to create a new snapshot
   * @return the requested page of audit events
   */
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
