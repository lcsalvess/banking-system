package com.lcsalvess.bankingsystem.service.transaction.query;

import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import org.springframework.data.domain.Page;

public record TransactionPageResult(
        Page<TransactionResponseDTO> page,
        String snapshotToken
) {
}