package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.transaction.AccountOperationRequestDTO;
import com.lucas.sistemabancario.dto.request.transaction.TransferRequestDTO;
import com.lucas.sistemabancario.dto.response.TransactionResponseDTO;
import com.lucas.sistemabancario.entity.Account;
import com.lucas.sistemabancario.entity.SavingsAccount;
import com.lucas.sistemabancario.entity.Transaction;
import com.lucas.sistemabancario.entity.enums.AccountStatus;
import com.lucas.sistemabancario.entity.enums.TransactionType;
import com.lucas.sistemabancario.exception.account.AccountIsNotActiveException;
import com.lucas.sistemabancario.exception.account.AccountIsNotSavingsException;
import com.lucas.sistemabancario.exception.account.AccountsAreSameException;
import com.lucas.sistemabancario.exception.transaction.YieldAlreadyAppliedException;
import com.lucas.sistemabancario.exception.transaction.YieldNotAvailableException;
import com.lucas.sistemabancario.exception.transaction.InsufficientBalanceException;
import com.lucas.sistemabancario.exception.transaction.InvalidAmountException;
import com.lucas.sistemabancario.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
    }

    @Transactional
    public TransactionResponseDTO deposit(AccountOperationRequestDTO dto) {
        Account account = accountService.findEntityByAccountNumber(dto.accountNumber());
        validateActiveAccount(account);
        validateAmount(dto.amount());
        account.credit(dto.amount());
        Transaction transaction = registerTransaction(TransactionType.DEPOSIT, dto.amount(), account);
        return TransactionResponseDTO.fromEntity(transaction);
    }

    public List<TransactionResponseDTO> findByAccountNumber(String accountNumber) {
        Account account = accountService.findEntityByAccountNumber(accountNumber);
        return transactionRepository.findByAccountId(account.getId())
                .stream()
                .map(TransactionResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public TransactionResponseDTO withdraw(AccountOperationRequestDTO dto) {
        Account account = accountService.findEntityByAccountNumber(dto.accountNumber());
        validateActiveAccount(account);
        validateAmount(dto.amount());
        validateBalance(account, dto.amount());
        account.debit(dto.amount());
        Transaction transaction = registerTransaction(TransactionType.WITHDRAWAL, dto.amount(), account);
        return TransactionResponseDTO.fromEntity(transaction);
    }

    @Transactional
    public TransactionResponseDTO transfer(TransferRequestDTO dto) {
        validateDistinctAccounts(dto.fromAccountNumber(), dto.toAccountNumber());
        Account fromAccount = accountService.findEntityByAccountNumber(dto.fromAccountNumber());
        Account toAccount = accountService.findEntityByAccountNumber(dto.toAccountNumber());
        validateActiveAccount(fromAccount);
        validateActiveAccount(toAccount);
        validateAmount(dto.amount());
        validateBalance(fromAccount, dto.amount());
        fromAccount.debit(dto.amount());
        toAccount.credit(dto.amount());
        Transaction sentTransaction = registerTransaction(TransactionType.TRANSFER_SENT, dto.amount(), fromAccount);
        registerTransaction(TransactionType.TRANSFER_RECEIVED, dto.amount(), toAccount);
        return TransactionResponseDTO.fromEntity(sentTransaction);
    }

    @Transactional
    public TransactionResponseDTO applyYield(String accountNumber) {
        Account account = accountService.findEntityByAccountNumber(accountNumber);
        SavingsAccount savingsAccount = validateAndGetSavingsAccount(account);
        validateActiveAccount(savingsAccount);
        validateYieldAlreadyApplied(savingsAccount.getId());
        validateYieldAvailable(savingsAccount);
        BigDecimal yieldAmount = savingsAccount.calculateYield();
        savingsAccount.credit(yieldAmount);
        savingsAccount.updateLastYieldDate();
        Transaction transaction = registerTransaction(TransactionType.YIELD, yieldAmount, savingsAccount);
        return TransactionResponseDTO.fromEntity(transaction);
    }

    private void validateActiveAccount(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountIsNotActiveException("A conta informada não está ativa.");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("O valor deve ser maior que zero.");
        }
    }

    private void validateBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("O valor informado é maior do que o saldo.");
        }
    }

    private void validateDistinctAccounts(String fromAccountNumber, String toAccountNumber) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new AccountsAreSameException("A conta de origem não pode ser igual à conta de destino.");
        }
    }

    private SavingsAccount validateAndGetSavingsAccount(Account account) {
        if (!(account instanceof SavingsAccount savingsAccount)) {
            throw new AccountIsNotSavingsException("A conta informada não é poupança.");
        }
        return savingsAccount;
    }

    private void validateYieldAvailable(SavingsAccount savingsAccount) {
        if (!savingsAccount.isEligibleForYield()) {
            throw new YieldNotAvailableException("A conta ainda não está disponível para receber rendimento");
        }
    }

    private void validateYieldAlreadyApplied(Long accountId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        boolean alreadyApplied = transactionRepository.existsByAccountIdAndTypeAndCreatedAtBetween(accountId, TransactionType.YIELD, startOfDay, endOfDay);
        if (alreadyApplied) {
            throw new YieldAlreadyAppliedException("O rendimento já foi aplicado para a conta hoje.");
        }
    }

    private Transaction registerTransaction(TransactionType type, BigDecimal amount, Account account) {
        Transaction transaction = new Transaction(type, amount, LocalDateTime.now(), account);
        return transactionRepository.save(transaction);
    }
}
