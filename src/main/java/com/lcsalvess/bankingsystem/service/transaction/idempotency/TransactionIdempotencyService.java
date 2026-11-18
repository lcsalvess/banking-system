package com.lcsalvess.bankingsystem.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDto;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDto;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDto;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.transaction.IdempotencyKeyConflictException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRecord;
import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates idempotent execution of financial transactions.
 *
 * <p>Prevents duplicate deposits, withdrawals, and transfers by associating
 * idempotency keys with authenticated users and request fingerprints.
 *
 * <p>Allows repeated requests with the same key and payload to recover
 * the original transaction result.
 */
@Service
public class TransactionIdempotencyService {

  private final TransactionIdempotencyRepository idempotencyRepository;
  private final TransactionRepository transactionRepository;
  private final TransactionService transactionService;
  private final TransactionQueryService transactionQueryService;
  private final CurrentUserService currentUserService;

  public TransactionIdempotencyService(
      TransactionIdempotencyRepository idempotencyRepository,
      TransactionRepository transactionRepository,
      TransactionService transactionService,
      TransactionQueryService transactionQueryService,
      CurrentUserService currentUserService
  ) {
    this.idempotencyRepository = idempotencyRepository;
    this.transactionRepository = transactionRepository;
    this.transactionService = transactionService;
    this.transactionQueryService = transactionQueryService;
    this.currentUserService = currentUserService;
  }

  /**
   * Executes a deposit using an idempotency key.
   *
   * <p>Repeated requests with the same key and payload return the original
   * transaction result without executing the deposit again.
   *
   * @param idempotencyKey the unique key identifying the operation
   * @param request the deposit request data
   * @return the response containing the resulting transaction
   * @throws IdempotencyKeyConflictException if the key was previously used
   *     with a different operation or request payload
   */
  @Transactional
  public TransactionResponseDto deposit(
      UUID idempotencyKey,
      AccountOperationRequestDto request
  ) {
    return execute(
        idempotencyKey,
        TransactionIdempotencyOperation.DEPOSIT,
        TransactionRequestFingerprint.forDeposit(request),
        () -> transactionService.deposit(request)
    );
  }

  /**
   * Executes a withdrawal using an idempotency key.
   *
   * <p>Repeated requests with the same key and payload return the original
   * transaction result without executing the withdrawal again.
   *
   * @param idempotencyKey the unique key identifying the operation
   * @param request the withdrawal request data
   * @return the response containing the resulting transaction
   * @throws IdempotencyKeyConflictException if the key was previously used
   *     with a different operation or request payload
   */
  @Transactional
  public TransactionResponseDto withdraw(
      UUID idempotencyKey,
      AccountOperationRequestDto request
  ) {
    return execute(
        idempotencyKey,
        TransactionIdempotencyOperation.WITHDRAWAL,
        TransactionRequestFingerprint.forWithdrawal(request),
        () -> transactionService.withdraw(request)
    );
  }

  /**
   * Executes a transfer using an idempotency key.
   *
   * <p>Repeated requests with the same key and payload return the original
   * transaction result without executing the transfer again.
   *
   * @param idempotencyKey the unique key identifying the operation
   * @param request the transfer request data
   * @return the response containing the resulting transaction
   * @throws IdempotencyKeyConflictException if the key was previously used
   *     with a different operation or request payload
   */
  @Transactional
  public TransactionResponseDto transfer(
      UUID idempotencyKey,
      TransferRequestDto request
  ) {
    return execute(
        idempotencyKey,
        TransactionIdempotencyOperation.TRANSFER,
        TransactionRequestFingerprint.forTransfer(request),
        () -> transactionService.transfer(request)
    );
  }

  private TransactionResponseDto execute(
      UUID idempotencyKey,
      TransactionIdempotencyOperation operation,
      String requestHash,
      Supplier<TransactionResponseDto> operationExecutor
  ) {
    User currentUser = currentUserService.getCurrentUser();
    long userId = currentUser.getId();

    boolean reserved = idempotencyRepository.tryReserve(
        userId,
        idempotencyKey,
        operation.name(),
        requestHash
    );

    if (!reserved) {
      return recoverExistingResult(
          userId,
          idempotencyKey,
          operation,
          requestHash
      );
    }

    TransactionResponseDto response = operationExecutor.get();

    if (response.transactionCode() == null) {
      throw new IllegalStateException(
          "The financial operation did not return a transaction code."
      );
    }

    /*
     * Ensures that the financial transaction is flushed to the database
     * before recording its reference in the idempotency table.
     * The foreign key for transaction_code must be able to reference the transaction.
     */
    transactionRepository.flush();

    idempotencyRepository.complete(
        userId,
        idempotencyKey,
        response.transactionCode()
    );

    return response;
  }

  private TransactionResponseDto recoverExistingResult(
      long userId,
      UUID idempotencyKey,
      TransactionIdempotencyOperation operation,
      String requestHash
  ) {
    TransactionIdempotencyRecord existing =
        idempotencyRepository.findByUserIdAndKey(
            userId,
            idempotencyKey
        ).orElseThrow(() -> new IllegalStateException(
            "The idempotency reservation was not found after a conflict."
        ));

    if (existing.transactionCode() == null) {
      throw new IllegalStateException(
          "Idempotency record marked as completed without an associated financial transaction."
      );
    }

    boolean sameOperation =
        existing.operationType().equals(operation.name());

    boolean sameRequest =
        existing.requestHash().equals(requestHash);

    if (!sameOperation || !sameRequest) {
      throw new IdempotencyKeyConflictException();
    }

    return transactionQueryService.findByTransactionCode(
        existing.transactionCode()
    );
  }
}
