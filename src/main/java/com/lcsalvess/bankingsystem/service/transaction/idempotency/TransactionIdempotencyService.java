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
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
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
                    "A operação financeira não retornou um código de transação."
            );
        }

        /*
         * Garante que a transação financeira seja enviada ao banco antes
         * de registrar sua referência na tabela de idempotência.
         * A FK para transaction_code precisa conseguir encontrar a transação.
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
                        "A reserva de idempotência não foi encontrada após um conflito."
                ));

        if (existing.transactionCode() == null) {
            throw new IllegalStateException(
                    "Registro de idempotência confirmado sem uma transação financeira associada."
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