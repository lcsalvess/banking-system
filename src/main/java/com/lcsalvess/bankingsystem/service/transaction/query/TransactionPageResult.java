package com.lcsalvess.bankingsystem.service.transaction.query;

import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDto;
import org.springframework.data.domain.Page;

/**
 * Represents a paginated transaction query result with its snapshot token.
 *
 * @param page the page containing the transaction responses
 * @param snapshotToken the token identifying the transaction snapshot
 *     used to maintain consistent pagination across requests
 */
public record TransactionPageResult(
    Page<TransactionResponseDto> page,
    String snapshotToken
) {
}
