package com.lcsalvess.bankingsystem.service.transaction.query;

import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDto;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides read-only operations for retrieving financial transactions.
 *
 * <p>Supports transaction lookup, filtering, date range queries, and
 * snapshot-aware pagination to maintain consistent results across pages.
 */
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
   * Retrieves transactions for an account using pagination and optional filters.
   *
   * <p>Compatibility overload that delegates to the snapshot-aware method
   * without an explicitly supplied snapshot token.
   *
   * @param accountNumber the account number
   * @param accountDigit the account check digit
   * @param type the optional transaction type filter
   * @param startDate the inclusive start date
   * @param endDate the inclusive end date
   * @param pageable the pagination and sorting configuration
   * @return a page of transaction responses matching the supplied filters
   */
  @Transactional(readOnly = true)
  public Page<TransactionResponseDto> findByAccountNumber(
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

  /**
   * Retrieves transactions for an account using snapshot-aware pagination.
   *
   * <p>Establishes a transaction ID boundary on the first request and
   * reuses that boundary when a valid snapshot token is supplied.
   * This prevents transactions created after the initial query from
   * appearing in subsequent pages.
   *
   * @param accountNumber the account number
   * @param accountDigit the account check digit
   * @param type the optional transaction type filter
   * @param startDate the inclusive start date
   * @param endDate the inclusive end date
   * @param pageable the pagination and sorting configuration
   * @param requestedSnapshotToken the snapshot token from a previous request,
   *     or {@code null} to establish a new snapshot
   * @return the transaction page and its associated snapshot token
   * @throws InvalidTransactionSnapshotException if the snapshot token is invalid
   */
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

    Page<TransactionResponseDto> page = transactionRepository.findAll(
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
    ).map(TransactionResponseDto::fromEntity);

    return new TransactionPageResult(page, snapshot.token());
  }

  private SnapshotBoundary resolveSnapshot(
      Account account,
      String requestedSnapshotToken
  ) {
    if (requestedSnapshotToken == null) {
      return transactionRepository
          .findFirstByAccountIdOrderByIdDesc(account.getId())
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
        .findByTransactionCodeAndAccountId(
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

  /**
   * Retrieves a transaction using its unique transaction code.
   *
   * @param transactionCode the unique identifier of the transaction
   * @return the response containing the transaction details
   * @throws TransactionNotFoundException if no transaction exists with the
   *     specified transaction code
   */
  @Transactional(readOnly = true)
  public TransactionResponseDto findByTransactionCode(
      UUID transactionCode
  ) {
    Transaction transaction = transactionRepository
        .findByTransactionCode(transactionCode)
        .orElseThrow(() -> new TransactionNotFoundException(
            ApiErrorMessages.TRANSACTION_NOT_FOUND
        ));

    return TransactionResponseDto.fromEntity(transaction);
  }

  private record SnapshotBoundary(
      String token,
      long maxTransactionId
  ) {
  }
}
