package com.lcsalvess.bankingsystem.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.transaction.IdempotencyKeyConflictException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRecord;
import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

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

    @Transactional
    public TransactionResponseDTO deposit(
            UUID idempotencyKey,
            AccountOperationRequestDTO request
    ) {
        return execute(
                idempotencyKey,
                TransactionIdempotencyOperation.DEPOSIT,
                TransactionRequestFingerprint.forDeposit(request),
                () -> transactionService.deposit(request)
        );
    }

    @Transactional
    public TransactionResponseDTO withdraw(
            UUID idempotencyKey,
            AccountOperationRequestDTO request
    ) {
        return execute(
                idempotencyKey,
                TransactionIdempotencyOperation.WITHDRAWAL,
                TransactionRequestFingerprint.forWithdrawal(request),
                () -> transactionService.withdraw(request)
        );
    }

    @Transactional
    public TransactionResponseDTO transfer(
            UUID idempotencyKey,
            TransferRequestDTO request
    ) {
        return execute(
                idempotencyKey,
                TransactionIdempotencyOperation.TRANSFER,
                TransactionRequestFingerprint.forTransfer(request),
                () -> transactionService.transfer(request)
        );
    }

    private TransactionResponseDTO execute(
            UUID idempotencyKey,
            TransactionIdempotencyOperation operation,
            String requestHash,
            Supplier<TransactionResponseDTO> operationExecutor
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

        TransactionResponseDTO response = operationExecutor.get();

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

    private TransactionResponseDTO recoverExistingResult(
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
