package com.lcsalvess.bankingsystem.service.audit.contract;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import org.springframework.data.domain.Page;

import java.util.Objects;

public record AuditEventPageResult(
        Page<AuditEventResponseDTO> page,
        String snapshotToken
) {

    public AuditEventPageResult {
        Objects.requireNonNull(page, "The page is required.");
        Objects.requireNonNull(snapshotToken, "The snapshot token is required.");
    }
}
