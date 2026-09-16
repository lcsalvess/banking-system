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
import com.lucas.sistemabancario.exception.transaction.InsufficientBalanceException;
import com.lucas.sistemabancario.exception.transaction.YieldAlreadyAppliedException;
import com.lucas.sistemabancario.exception.transaction.YieldNotAvailableException;
import com.lucas.sistemabancario.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountService accountService;
    @InjectMocks
    private TransactionService transactionService;

    private AccountOperationRequestDTO createOperationDTO(String accountNumber, String accountDigit, BigDecimal amount) {
        return new AccountOperationRequestDTO(accountNumber, accountDigit, amount);
    }

    @Nested
    @DisplayName("Ao realizar um depósito")
    class DepositTests {
        private final String accountNumber = "00001";
        private final String accountDigit = "1";
        private Account account;

        @BeforeEach
        void setUp() {
            account = new Account() {
            };
            ReflectionTestUtils.setField(account, "accountNumber", accountNumber);
            ReflectionTestUtils.setField(account, "accountDigit", accountDigit);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar em uma conta cancelada.")
        void shouldThrowExceptionWhenDepositingToInactiveAccount() {
            ReflectionTestUtils.setField(account, "status", AccountStatus.CANCELLED);
            when(accountService.findEntityByAccountNumber(accountNumber, accountDigit)).thenReturn(account);

            AccountOperationRequestDTO dto = createOperationDTO(accountNumber, accountDigit, BigDecimal.TEN);

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.deposit(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar depósito em uma conta ativa e valor válido.")
        void shouldDepositSuccessfullyWhenAccountIsActiveAndAmountIsValid() {
            when(accountService.findEntityByAccountNumber(accountNumber, accountDigit)).thenReturn(account);
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

            AccountOperationRequestDTO dto = createOperationDTO(accountNumber, accountDigit, BigDecimal.TEN);

            TransactionResponseDTO result = transactionService.deposit(dto);

            assertEquals(BigDecimal.TEN, account.getBalance());
            assertNotNull(result);
            assertEquals(BigDecimal.TEN, result.amount());
            assertEquals(TransactionType.DEPOSIT, result.transactionType());
            verify(accountService).findEntityByAccountNumber(accountNumber, accountDigit);
            verify(transactionRepository, times(1)).save(any(Transaction.class));
        }
    }

    @Nested
    @DisplayName("Ao realizar um saque")
    class WithdrawTests {
        private final String accountNumber = "00001";
        private final String accountDigit = "1";
        private final Long accountId = 1L;
        private Account account;

        @BeforeEach
        void setUp() {
            account = new Account() {
            };
            ReflectionTestUtils.setField(account, "id", accountId);
            ReflectionTestUtils.setField(account, "accountNumber", accountNumber);
            ReflectionTestUtils.setField(account, "balance", new BigDecimal("100.00"));
            when(accountService.findEntityByAccountNumber(accountNumber, accountDigit)).thenReturn(account);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar de uma conta cancelada")
        void shouldThrowExceptionWhenWithdrawingFromInactiveAccount() {
            ReflectionTestUtils.setField(account, "status", AccountStatus.CANCELLED);
            AccountOperationRequestDTO dto = createOperationDTO(accountNumber, accountDigit, BigDecimal.TEN);

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.withdraw(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar mais do que tem em conta")
        void shouldThrowExceptionWhenWithdrawalAmountExceedsBalance() {
            AccountOperationRequestDTO dto = createOperationDTO(accountNumber, accountDigit, new BigDecimal("200.00"));

            assertThrows(InsufficientBalanceException.class, () -> transactionService.withdraw(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar saque com sucesso quando o valor for igual ao saldo")
        void shouldWithdrawSuccessfullyWhenAmountEqualsBalance() {
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            AccountOperationRequestDTO dto = createOperationDTO(accountNumber, accountDigit, new BigDecimal("100.00"));

            transactionService.withdraw(dto);

            assertEquals(new BigDecimal("0.00"), account.getBalance());
            verify(transactionRepository, times(1)).save(any(Transaction.class));
        }

        @Test
        @DisplayName("Deve realizar saque com sucesso quando o valor for menor que o saldo")
        void shouldWithdrawSuccessfullyWhenAmountIsLessThanBalance() {
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            AccountOperationRequestDTO dto = createOperationDTO(accountNumber, accountDigit, new BigDecimal("30.00"));

            transactionService.withdraw(dto);

            assertEquals(new BigDecimal("70.00"), account.getBalance());
            verify(transactionRepository, times(1)).save(any(Transaction.class));
        }
    }

    @Nested
    @DisplayName("Ao realizar uma transferência")
    class TransferTests {
        private final String fromAccountNumber = "00001";
        private final String fromAccountDigit = "1";
        private final String toAccountNumber = "00002";
        private final String toAccountDigit = "2";
        private Account fromAccount;
        private Account toAccount;

        @BeforeEach
        void setUp() {
            fromAccount = new Account() {
            };
            toAccount = new Account() {
            };
            ReflectionTestUtils.setField(fromAccount, "accountNumber", fromAccountNumber);
            ReflectionTestUtils.setField(fromAccount, "balance", new BigDecimal("100.00"));
            ReflectionTestUtils.setField(toAccount, "accountNumber", toAccountNumber);
            ReflectionTestUtils.setField(toAccount, "balance", new BigDecimal("50.00"));
        }

        private TransferRequestDTO createTransferRequestDTO(BigDecimal amount) {
            return new TransferRequestDTO(fromAccountNumber, fromAccountDigit, toAccountNumber, toAccountDigit, amount);
        }

        @Test
        @DisplayName("Deve lançar exceção se a transferência for entre a mesma conta")
        void shouldThrowExceptionWhenTransferringBetweenSameAccounts() {
            TransferRequestDTO dto = new TransferRequestDTO(fromAccountNumber, fromAccountDigit, fromAccountNumber, fromAccountDigit, BigDecimal.TEN);
            assertThrows(AccountsAreSameException.class, () -> transactionService.transfer(dto));
            verify(accountService, never()).findEntityByAccountNumber(any(), any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção se a conta origem está cancelada.")
        void shouldThrowExceptionWhenSourceAccountIsInactive() {
            mockAccountLookup();
            ReflectionTestUtils.setField(fromAccount, "status", AccountStatus.CANCELLED);
            TransferRequestDTO dto = createTransferRequestDTO(BigDecimal.TEN);

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.transfer(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção se a conta destino está cancelada.")
        void shouldThrowExceptionWhenDestinationAccountIsInactive() {
            mockAccountLookup();
            ReflectionTestUtils.setField(toAccount, "status", AccountStatus.CANCELLED);
            TransferRequestDTO dto = createTransferRequestDTO(BigDecimal.TEN);

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.transfer(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar transferir valor maior que o saldo da conta de origem")
        void shouldThrowExceptionWhenTransferAmountExceedsSourceAccountBalance() {
            mockAccountLookup();
            TransferRequestDTO dto = createTransferRequestDTO(new BigDecimal("200.00"));

            assertThrows(InsufficientBalanceException.class, () -> transactionService.transfer(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar transferência com sucesso, alterar dados e salvar duas transações.")
        void shouldTransferSuccessfully() {
            mockAccountLookup();
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

            BigDecimal amount = new BigDecimal("30.00");
            TransferRequestDTO dto = createTransferRequestDTO(amount);

            transactionService.transfer(dto);

            assertEquals(new BigDecimal("70.00"), fromAccount.getBalance());
            assertEquals(new BigDecimal("80.00"), toAccount.getBalance());
            verify(transactionRepository, times(2)).save(any(Transaction.class));
        }

        private void mockAccountLookup() {
            when(accountService.findEntityByAccountNumber(fromAccountNumber, fromAccountDigit)).thenReturn(fromAccount);
            when(accountService.findEntityByAccountNumber(toAccountNumber, toAccountDigit)).thenReturn(toAccount);
        }
    }

    @Nested
    @DisplayName("Ao aplicar rendimento")
    class ApplyYieldTests {
        private final Long accountId = 1L;
        private final String accountNumber = "00002";
        private final String accountDigit = "2";
        private SavingsAccount savingsAccount;

        @BeforeEach
        void setUp() {
            savingsAccount = spy(new SavingsAccount());
            ReflectionTestUtils.setField(savingsAccount, "id", accountId);
            ReflectionTestUtils.setField(savingsAccount, "accountNumber", accountNumber);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar aplicar rendimento em uma conta que não é poupança.")
        void shouldThrowExceptionWhenAccountIsNotSavingsAccount() {
            Account invalidAccount = new Account() {
            };
            ReflectionTestUtils.setField(invalidAccount, "id", accountId);
            when(accountService.findEntityByAccountNumber(accountNumber, accountDigit)).thenReturn(invalidAccount);

            assertThrows(AccountIsNotSavingsException.class, () -> transactionService.applyYield(accountNumber, accountDigit));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar aplicar rendimento já aplicado no dia")
        void shouldThrowExceptionWhenYieldWasAlreadyAppliedToday() {
            mockSavingsAccountLookup();
            when(transactionRepository.existsByAccountIdAndTypeAndCreatedAtBetween(eq(accountId), any(), any(), any())).thenReturn(true);

            assertThrows(YieldAlreadyAppliedException.class, () -> transactionService.applyYield(accountNumber, accountDigit));
            verify(savingsAccount, never()).credit(any());
            verify(savingsAccount, never()).updateLastYieldDate();
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção quando conta poupança estiver cancelada.")
        void shouldThrowExceptionWhenSavingsAccountIsInactive() {
            ReflectionTestUtils.setField(savingsAccount, "status", AccountStatus.CANCELLED);
            mockSavingsAccountLookup();

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.applyYield(accountNumber, accountDigit));
            verify(savingsAccount, never()).credit(any());
            verify(savingsAccount, never()).updateLastYieldDate();
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção quando a conta poupança não puder receber rendimento.")
        void shouldThrowExceptionWhenSavingsAccountIsNotEligibleForYield() {
            mockSavingsAccountLookup();
            doReturn(false).when(savingsAccount).isEligibleForYield();

            assertThrows(YieldNotAvailableException.class, () -> transactionService.applyYield(accountNumber, accountDigit));
            verify(savingsAccount, never()).credit(any());
            verify(savingsAccount, never()).updateLastYieldDate();
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção quando o rendimento da conta poupança for zero.")
        void shouldThrowExceptionWhenYieldIsZero() {
            mockSavingsAccountLookup();
            doReturn(true).when(savingsAccount).isEligibleForYield();
            doReturn(BigDecimal.ZERO).when(savingsAccount).calculateYield();
            assertThrows(YieldNotAvailableException.class, () -> transactionService.applyYield(accountNumber, accountDigit));
            verify(savingsAccount, never()).credit(any());
            verify(savingsAccount, never()).updateLastYieldDate();
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve aplicar rendimento com sucesso quando ainda não foi aplicado hoje.")
        void shouldApplyYieldSuccessfully() {
            mockSavingsAccountLookup();
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

            BigDecimal yieldAmount = new BigDecimal("15.50");
            doReturn(true).when(savingsAccount).isEligibleForYield();
            doReturn(yieldAmount).when(savingsAccount).calculateYield();
            when(transactionRepository.existsByAccountIdAndTypeAndCreatedAtBetween(eq(accountId), any(), any(), any()))
                    .thenReturn(false);
            BigDecimal initialBalance = savingsAccount.getBalance();

            transactionService.applyYield(accountNumber, accountDigit);

            assertEquals(initialBalance.add(yieldAmount), savingsAccount.getBalance());
            verify(savingsAccount).credit(any());
            verify(savingsAccount).updateLastYieldDate();
            verify(transactionRepository).save(any(Transaction.class));
        }

        private void mockSavingsAccountLookup() {
            when(accountService.findEntityByAccountNumber(accountNumber, accountDigit)).thenReturn(savingsAccount);
        }
    }
}