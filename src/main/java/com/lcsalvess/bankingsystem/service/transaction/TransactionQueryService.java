package com.lcsalvess.bankingsystem.service.transaction;

import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
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

    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> findByAccountNumber(
            String accountNumber,
            String accountDigit,
            TransactionType type,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        QueryDateRangeValidator.validate(startDate, endDate, clock);

        Account account = accountService.findEntityByAccountNumber(
                accountNumber,
                accountDigit
        );

        return transactionRepository.findAll(
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
                                : null
                ),
                PageableTieBreaker.withIdTieBreaker(pageable)
        ).map(TransactionResponseDTO::fromEntity);
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
}
