package com.lcsalvess.bankingsystem.service.transaction.query;

import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.InvalidTransactionSnapshotException;
import com.lcsalvess.bankingsystem.exception.transaction.TransactionNotFoundException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.specification.TransactionSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import com.lcsalvess.bankingsystem.validation.daterange.QueryDateRangeValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class TransactionQueryService {

    private static final String SNAPSHOT_TOKEN_PREFIX = "v1:";
    private static final String EMPTY_SNAPSHOT_TOKEN_PREFIX = "v1:empty:";
    private static final long EMPTY_SNAPSHOT_MAX_ID = 0L;

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final Clock clock;

    public TransactionQueryService(
            TransactionRepository transactionRepository,
            AccountService accountService,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.clock = clock;
    }

    /**
     * Compatibility overload. The controller will use the snapshot-aware
     * overload so the token can be returned to the client.
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> findByAccountNumber(
            String accountNumber,
            String accountDigit,
            TransactionType type,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        return findByAccountNumber(
                accountNumber,
                accountDigit,
                type,
                startDate,
                endDate,
                pageable,
                null
        ).page();
    }

    @Transactional(readOnly = true)
    public TransactionPageResult findByAccountNumber(
            String accountNumber,
            String accountDigit,
            TransactionType type,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable,
            String requestedSnapshotToken
    ) {
        QueryDateRangeValidator.validate(startDate, endDate, clock);

        Account account = accountService.findEntityByAccountNumber(
                accountNumber,
                accountDigit
        );

        SnapshotBoundary snapshot = resolveSnapshot(
                account,
                requestedSnapshotToken
        );

        Page<TransactionResponseDTO> page = transactionRepository.findAll(
                TransactionSpecifications.byAccountAndFilters(
                        account.getId(),
                        type,
                        startDate != null
                                ? startDate.atStartOfDay(AppTimeZone.ZONE)
                                .toInstant()
                                : null,
                        endDate != null
                                ? endDate.plusDays(1)
                                .atStartOfDay(AppTimeZone.ZONE)
                                .toInstant()
                                : null,
                        snapshot.maxTransactionId()
                ),
                PageableTieBreaker.withIdTieBreaker(pageable)
        ).map(TransactionResponseDTO::fromEntity);

        return new TransactionPageResult(page, snapshot.token());
    }

    private SnapshotBoundary resolveSnapshot(
            Account account,
            String requestedSnapshotToken
    ) {
        if (requestedSnapshotToken == null) {
            return transactionRepository
                    .findFirstByAccount_IdOrderByIdDesc(account.getId())
                    .map(transaction -> new SnapshotBoundary(
                            SNAPSHOT_TOKEN_PREFIX
                                    + transaction.getTransactionCode(),
                            transaction.getId()
                    ))
                    .orElseGet(() -> new SnapshotBoundary(
                            emptySnapshotToken(account),
                            EMPTY_SNAPSHOT_MAX_ID
                    ));
        }

        String expectedEmptySnapshotToken = emptySnapshotToken(account);

        if (expectedEmptySnapshotToken.equals(requestedSnapshotToken)) {
            return new SnapshotBoundary(
                    expectedEmptySnapshotToken,
                    EMPTY_SNAPSHOT_MAX_ID
            );
        }

        if (!requestedSnapshotToken.startsWith(SNAPSHOT_TOKEN_PREFIX)) {
            throw invalidSnapshotToken();
        }

        String transactionCodeValue = requestedSnapshotToken.substring(
                SNAPSHOT_TOKEN_PREFIX.length()
        );

        UUID transactionCode;

        try {
            transactionCode = UUID.fromString(transactionCodeValue);
        } catch (IllegalArgumentException exception) {
            throw invalidSnapshotToken();
        }

        Transaction boundaryTransaction = transactionRepository
                .findByTransactionCodeAndAccount_Id(
                        transactionCode,
                        account.getId()
                )
                .orElseThrow(this::invalidSnapshotToken);

        return new SnapshotBoundary(
                SNAPSHOT_TOKEN_PREFIX + boundaryTransaction.getTransactionCode(),
                boundaryTransaction.getId()
        );
    }

    private String emptySnapshotToken(Account account) {
        return EMPTY_SNAPSHOT_TOKEN_PREFIX
                + account.getAccountNumber()
                + ":"
                + account.getDigit();
    }

    private InvalidTransactionSnapshotException invalidSnapshotToken() {
        return new InvalidTransactionSnapshotException(
                ApiErrorMessages.INVALID_TRANSACTION_SNAPSHOT
        );
    }

    @Transactional(readOnly = true)
    public TransactionResponseDTO findByTransactionCode(
            UUID transactionCode
    ) {
        Transaction transaction = transactionRepository
                .findByTransactionCode(transactionCode)
                .orElseThrow(() -> new TransactionNotFoundException(
                        ApiErrorMessages.TRANSACTION_NOT_FOUND
                ));

        return TransactionResponseDTO.fromEntity(transaction);
    }

    private record SnapshotBoundary(
            String token,
            long maxTransactionId
    ) {
    }
}
