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
import com.lucas.sistemabancario.exception.transaction.InterestAlreadyAppliedException;
import com.lucas.sistemabancario.exception.transaction.InterestNotAvailableException;
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

    private AccountOperationRequestDTO createOperationDTO(Long accountId, BigDecimal amount) {
        return new AccountOperationRequestDTO(accountId, amount);
    }

    @Nested
    @DisplayName("Ao realizar um depósito")
    class DepositTests {
        private final Long accountId = 1L;
        private Account account;

        @BeforeEach
        void setUp() {
            account = new Account() {
            };
            ReflectionTestUtils.setField(account, "id", accountId);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar em uma conta CANCELADA.")
        void shouldThrowExceptionWhenDepositingToInactiveAccount() {
            ReflectionTestUtils.setField(account, "status", AccountStatus.CANCELLED);
            when(accountService.findEntityById(accountId)).thenReturn(account);

            AccountOperationRequestDTO dto = createOperationDTO(accountId, BigDecimal.TEN);

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.deposit(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar depósito em uma conta ativa e valor válido.")
        void shouldDepositSuccessfullyWhenAccountIsActiveAndAmountIsValid() {
            when(accountService.findEntityById(accountId)).thenReturn(account);
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

            AccountOperationRequestDTO dto = createOperationDTO(accountId, BigDecimal.TEN);

            TransactionResponseDTO result = transactionService.deposit(dto);

            assertEquals(BigDecimal.TEN, account.getBalance());
            assertNotNull(result);
            assertEquals(BigDecimal.TEN, result.amount());
            assertEquals(TransactionType.DEPOSIT, result.transactionType());
            verify(transactionRepository, times(1)).save(any(Transaction.class));
        }
    }

    @Nested
    @DisplayName("Ao realizar um saque")
    class WithdrawTests {
        private final Long accountId = 1L;
        private Account account;

        @BeforeEach
        void setUp() {
            account = new Account() {
            };
            ReflectionTestUtils.setField(account, "id", accountId);
            ReflectionTestUtils.setField(account, "balance", new BigDecimal("100.00"));
            when(accountService.findEntityById(accountId)).thenReturn(account);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar de uma conta cancelada")
        void shouldThrowExceptionWhenWithdrawingFromInactiveAccount() {
            ReflectionTestUtils.setField(account, "status", AccountStatus.CANCELLED);
            AccountOperationRequestDTO dto = createOperationDTO(accountId, BigDecimal.TEN);

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.withdraw(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar mais do que tem em conta")
        void shouldThrowExceptionWhenWithdrawalAmountExceedsBalance() {
            AccountOperationRequestDTO dto = createOperationDTO(accountId, new BigDecimal("200.00"));

            assertThrows(InsufficientBalanceException.class, () -> transactionService.withdraw(dto));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar saque com sucesso quando o valor for igual ao saldo")
        void shouldWithdrawSuccessfullyWhenAmountEqualsBalance() {
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            AccountOperationRequestDTO dto = createOperationDTO(accountId, new BigDecimal("100.00"));

            transactionService.withdraw(dto);

            assertEquals(new BigDecimal("0.00"), account.getBalance());
            verify(transactionRepository, times(1)).save(any(Transaction.class));
        }

        @Test
        @DisplayName("Deve realizar saque com sucesso quando o valor for menor que o saldo")
        void shouldWithdrawSuccessfullyWhenAmountIsLessThanBalance() {
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            AccountOperationRequestDTO dto = createOperationDTO(accountId, new BigDecimal("30.00"));

            transactionService.withdraw(dto);

            assertEquals(new BigDecimal("70.00"), account.getBalance());
            verify(transactionRepository, times(1)).save(any(Transaction.class));
        }
    }

    @Nested
    @DisplayName("Ao realizar uma transferência")
    class TransferTests {
        private final Long fromAccountId = 1L;
        private final Long toAccountId = 2L;
        private Account fromAccount;
        private Account toAccount;

        @BeforeEach
        void setUp() {
            fromAccount = new Account() {
            };
            toAccount = new Account() {
            };
            ReflectionTestUtils.setField(fromAccount, "id", fromAccountId);
            ReflectionTestUtils.setField(fromAccount, "balance", new BigDecimal("100.00"));
            ReflectionTestUtils.setField(toAccount, "id", toAccountId);
            ReflectionTestUtils.setField(toAccount, "balance", new BigDecimal("50.00"));
        }

        private TransferRequestDTO createTransferRequestDTO(BigDecimal amount) {
            return new TransferRequestDTO(fromAccountId, toAccountId, amount);
        }

        @Test
        @DisplayName("Deve lançar exceção se a transferência for entre a mesma conta")
        void shouldThrowExceptionWhenTransferringBetweenSameAccounts() {
            TransferRequestDTO dto = new TransferRequestDTO(fromAccountId, fromAccountId, BigDecimal.TEN);
            assertThrows(AccountsAreSameException.class, () -> transactionService.transfer(dto));
            verify(accountService, never()).findEntityById(any());
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
            when(accountService.findEntityById(fromAccountId)).thenReturn(fromAccount);
            when(accountService.findEntityById(toAccountId)).thenReturn(toAccount);
        }
    }

    @Nested
    @DisplayName("Ao aplicar rendimento")
    class ApplyYieldTests {
        private final Long accountId = 1L;
        private SavingsAccount savingsAccount;

        @BeforeEach
        void setUp() {
            savingsAccount = spy(new SavingsAccount());
            ReflectionTestUtils.setField(savingsAccount, "id", accountId);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar aplicar rendimento em uma conta que não é poupança.")
        void shouldThrowExceptionWhenAccountIsNotSavingsAccount() {
            Account invalidAccount = new Account() {};
            ReflectionTestUtils.setField(invalidAccount, "id", accountId);
            when(accountService.findEntityById(accountId)).thenReturn(invalidAccount);

            assertThrows(AccountIsNotSavingsException.class, () -> transactionService.applyYield(accountId));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar aplicar rendimento já aplicado no dia")
        void shouldThrowExceptionWhenYieldWasAlreadyAppliedToday() {
            mockSavingsAccountLookup();
            when(transactionRepository.existsByAccountIdAndTypeAndCreatedAtBetween(eq(accountId), any(), any(), any())).thenReturn(true);

            assertThrows(InterestAlreadyAppliedException.class, () -> transactionService.applyYield(accountId));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção quando conta poupança estiver cancelada.")
        void shouldThrowExceptionWhenSavingsAccountIsInactive() {
            ReflectionTestUtils.setField(savingsAccount, "status", AccountStatus.CANCELLED);
            mockSavingsAccountLookup();

            assertThrows(AccountIsNotActiveException.class, () -> transactionService.applyYield(accountId));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção quando a conta poupança não puder receber rendimento.")
        void shouldThrowExceptionWhenSavingsAccountIsNotEligibleForYield() {
            mockSavingsAccountLookup();
            doReturn(false).when(savingsAccount).isEligibleForYield();

            assertThrows(InterestNotAvailableException.class, () -> transactionService.applyYield(accountId));
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

            transactionService.applyYield(accountId);

            assertEquals(yieldAmount, savingsAccount.getBalance());
            verify(transactionRepository).save(any(Transaction.class));
        }

        private void mockSavingsAccountLookup() {
            when(accountService.findEntityById(accountId)).thenReturn(savingsAccount);
        }
    }
}