package com.lcsalvess.bankingsystem.service.audit.contract;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDto;
import java.util.Objects;
import org.springframework.data.domain.Page;

/**
 * Represents a paginated audit event result associated with a consistent snapshot.
 *
 * @param page paginated audit event response data
 * @param snapshotToken token identifying the audit query snapshot
 */
public record AuditEventPageResult(
    Page<AuditEventResponseDto> page,
    String snapshotToken
) {

  public AuditEventPageResult {
    Objects.requireNonNull(page, "The page is required.");
    Objects.requireNonNull(snapshotToken, "The snapshot token is required.");
  }
}
