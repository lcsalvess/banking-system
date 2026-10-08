package com.lcsalvess.bankingsystem.unit.service.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.Account;
import com.lcsalvess.bankingsystem.entity.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.Transaction;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotSavingsException;
import com.lcsalvess.bankingsystem.exception.account.AccountNotFoundException;
import com.lcsalvess.bankingsystem.exception.account.AccountsAreSameException;
import com.lcsalvess.bankingsystem.exception.transaction.*;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.LockedAccounts;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTests {

    private static final String PROVIDER = "com.lcsalvess.bankingsystem.unit.service.transaction.TransactionServiceTests#";

    private static final String USERNAME = "usuario.teste";

    private static final String ACCOUNT_NUMBER = "00001";
    private static final String ACCOUNT_DIGIT = "9";
    private static final Long ACCOUNT_ID = 1L;
    private static final String DESTINATION_NUMBER = "00003";
    private static final String DESTINATION_DIGIT = "5";
    private static final Long DESTINATION_ID = 3L;
    private static final String SAVINGS_NUMBER = "00002";
    private static final String SAVINGS_DIGIT = "7";
    private static final Long SAVINGS_ID = 2L;

    private static final BigDecimal OPERATION_AMOUNT = new BigDecimal("10.00");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 15, 10, 30, 45);

    private static final String ACCOUNT_NOT_FOUND_MESSAGE = "Conta não encontrada.";
    private static final String ACCOUNT_NOT_ACTIVE_MESSAGE = "A conta informada não está ativa.";
    private static final String INVALID_AMOUNT_MESSAGE = "O valor deve ser maior que zero.";
    private static final String INSUFFICIENT_BALANCE_MESSAGE = "O valor informado é maior do que o saldo.";
    private static final String ACCOUNTS_ARE_SAME_MESSAGE = "A conta de origem não pode ser igual à conta de destino.";
    private static final String NOT_SAVINGS_MESSAGE = "A conta informada não é poupança.";
    private static final String YIELD_ALREADY_APPLIED_MESSAGE = "O rendimento já foi aplicado para a conta hoje.";
    private static final String YIELD_NOT_ELIGIBLE_MESSAGE = "A conta ainda não está disponível para receber rendimento.";
    private static final String YIELD_NOT_AVAILABLE_MESSAGE = "Não há rendimento disponível para esta conta.";
    private static final String TRANSACTION_NOT_FOUND_MESSAGE = "Transação não encontrada.";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransactionService transactionService;

    @Nested
    @DisplayName("deposit(AccountOperationRequestDTO)")
    class Deposit {

        @Test
        @DisplayName("Should deposit successfully")
        void shouldDepositSuccessfully() {
            CheckingAccount account = account();
            AccountOperationRequestDTO request = operationRequest(OPERATION_AMOUNT);

            stubAccountOperationLookup(account);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            stubSaveReturningArgument();

            TransactionResponseDTO result = transactionService.deposit(request);

            assertBalance("110.00", account);

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            verifyAccountOperationLookup(account);
            verify(currentUserService).getUsername();
            verify(transactionRepository).save(transactionCaptor.capture());
            verify(eventPublisher).publishEvent(
                    new TransactionOperationEvent(TransactionType.DEPOSIT, ACCOUNT_NUMBER, OPERATION_AMOUNT, USERNAME)
            );
            verifyNoMoreInteractionsOnMocks();

            assertTransaction(transactionCaptor.getValue(), TransactionType.DEPOSIT, OPERATION_AMOUNT, account);
            assertEquals(response(transactionCaptor.getValue()), result);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAmounts")
        @DisplayName("Should throw when amount is invalid")
        void shouldThrowWhenAmountIsInvalid(String scenario, BigDecimal amount) {
            CheckingAccount account = account();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    InvalidAmountException.class,
                    INVALID_AMOUNT_MESSAGE,
                    () -> transactionService.deposit(operationRequest(amount))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account is not active")
        void shouldThrowWhenAccountIsNotActive() {
            CheckingAccount account = account();
            account.cancel();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ACCOUNT_NOT_ACTIVE_MESSAGE,
                    () -> transactionService.deposit(operationRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("withdraw(AccountOperationRequestDTO)")
    class Withdraw {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "withdrawals")
        @DisplayName("Should withdraw successfully")
        void shouldWithdrawSuccessfully(String scenario, BigDecimal amount, String expectedBalance) {
            CheckingAccount account = account();

            stubAccountOperationLookup(account);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            stubSaveReturningArgument();

            TransactionResponseDTO result = transactionService.withdraw(operationRequest(amount));

            assertBalance(expectedBalance, account);

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            verifyAccountOperationLookup(account);
            verify(currentUserService).getUsername();
            verify(transactionRepository).save(transactionCaptor.capture());
            verify(eventPublisher).publishEvent(
                    new TransactionOperationEvent(TransactionType.WITHDRAWAL, ACCOUNT_NUMBER, amount, USERNAME)
            );
            verifyNoMoreInteractionsOnMocks();

            assertTransaction(transactionCaptor.getValue(), TransactionType.WITHDRAWAL, amount, account);
            assertEquals(response(transactionCaptor.getValue()), result);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAmounts")
        @DisplayName("Should throw when amount is invalid")
        void shouldThrowWhenAmountIsInvalid(String scenario, BigDecimal amount) {
            CheckingAccount account = account();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    InvalidAmountException.class,
                    INVALID_AMOUNT_MESSAGE,
                    () -> transactionService.withdraw(operationRequest(amount))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "amountsAboveBalance")
        @DisplayName("Should throw when amount exceeds balance")
        void shouldThrowWhenAmountExceedsBalance(String scenario, BigDecimal amount) {
            CheckingAccount account = account();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    InsufficientBalanceException.class,
                    INSUFFICIENT_BALANCE_MESSAGE,
                    () -> transactionService.withdraw(operationRequest(amount))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account is not active")
        void shouldThrowWhenAccountIsNotActive() {
            CheckingAccount account = account();
            account.cancel();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ACCOUNT_NOT_ACTIVE_MESSAGE,
                    () -> transactionService.withdraw(operationRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("transfer(TransferRequestDTO)")
    class Transfer {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "transfers")
        @DisplayName("Should transfer successfully")
        void shouldTransferSuccessfully(
                String scenario,
                BigDecimal amount,
                String expectedSourceBalance,
                String expectedDestinationBalance
        ) {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();

            stubTransferAccountLookup(source, destination);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            stubSaveReturningArgument();

            TransactionResponseDTO result = transactionService.transfer(transferRequest(amount));

            assertBalance(expectedSourceBalance, source);
            assertBalance(expectedDestinationBalance, destination);

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            verifyTransferAccountLookup(source, destination);
            verify(currentUserService).getUsername();
            verify(transactionRepository, times(2)).save(transactionCaptor.capture());
            verify(eventPublisher).publishEvent(new TransactionTransferEvent(
                    TransactionType.TRANSFER_SENT, ACCOUNT_NUMBER, DESTINATION_NUMBER, amount, USERNAME
            ));
            verifyNoMoreInteractionsOnMocks();

            List<Transaction> saved = transactionCaptor.getAllValues();

            Transaction sent = saved.get(0);
            Transaction received = saved.get(1);

            assertTransaction(sent, TransactionType.TRANSFER_SENT, amount, source);
            assertTransaction(received, TransactionType.TRANSFER_RECEIVED, amount, destination);

            assertNotNull(sent.getTransferCode());
            assertEquals(sent.getTransferCode(), received.getTransferCode());

            assertNotEquals(sent.getTransactionCode(), received.getTransactionCode());

            assertEquals(response(sent), result);
        }

        @Test
        @DisplayName("Should throw when accounts are the same")
        void shouldThrowWhenAccountsAreTheSame() {
            TransferRequestDTO request = new TransferRequestDTO(
                    ACCOUNT_NUMBER, ACCOUNT_DIGIT, ACCOUNT_NUMBER, ACCOUNT_DIGIT, OPERATION_AMOUNT
            );

            assertThrowsWithMessage(
                    AccountsAreSameException.class,
                    ACCOUNTS_ARE_SAME_MESSAGE,
                    () -> transactionService.transfer(request)
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when source account is not active")
        void shouldThrowWhenSourceAccountIsNotActive() {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();
            source.cancel();

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ACCOUNT_NOT_ACTIVE_MESSAGE,
                    () -> transactionService.transfer(transferRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", source);
            assertBalance("50.00", destination);

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when destination account is not active")
        void shouldThrowWhenDestinationAccountIsNotActive() {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();
            destination.cancel();

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ACCOUNT_NOT_ACTIVE_MESSAGE,
                    () -> transactionService.transfer(transferRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", source);
            assertBalance("50.00", destination);

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAmounts")
        @DisplayName("Should throw when amount is invalid")
        void shouldThrowWhenAmountIsInvalid(String scenario, BigDecimal amount) {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    InvalidAmountException.class,
                    INVALID_AMOUNT_MESSAGE,
                    () -> transactionService.transfer(transferRequest(amount))
            );

            assertBalance("100.00", source);
            assertBalance("50.00", destination);

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "amountsAboveBalance")
        @DisplayName("Should throw when amount exceeds source balance")
        void shouldThrowWhenAmountExceedsSourceBalance(String scenario, BigDecimal amount) {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    InsufficientBalanceException.class,
                    INSUFFICIENT_BALANCE_MESSAGE,
                    () -> transactionService.transfer(transferRequest(amount))
            );

            assertBalance("100.00", source);
            assertBalance("50.00", destination);

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should not change balances when destination account does not exist")
        void shouldNotChangeBalancesWhenDestinationAccountDoesNotExist() {
            CheckingAccount source = account();

            when(accountService.lockAccountsForTransfer(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT,
                    DESTINATION_NUMBER,
                    DESTINATION_DIGIT
            )).thenThrow(
                    new AccountNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE)
            );

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ACCOUNT_NOT_FOUND_MESSAGE,
                    () -> transactionService.transfer(transferRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", source);

            verify(accountService).lockAccountsForTransfer(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT,
                    DESTINATION_NUMBER,
                    DESTINATION_DIGIT
            );

            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findByAccountNumber(String, String)")
    class FindByAccountNumber {

        @Test
        @DisplayName("Should return the account transactions")
        void shouldReturnTheAccountTransactions() {
            CheckingAccount account = account();
            Transaction deposit = new Transaction(TransactionType.DEPOSIT, new BigDecimal("10.00"), CREATED_AT, account);
            Transaction withdrawal = new Transaction(TransactionType.WITHDRAWAL, new BigDecimal("5.00"), CREATED_AT, account);

            stubAccountLookup(account);
            when(transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(ACCOUNT_ID)).thenReturn(List.of(deposit, withdrawal));

            List<TransactionResponseDTO> result = transactionService.findByAccountNumber(ACCOUNT_NUMBER, ACCOUNT_DIGIT);

            assertEquals(List.of(response(deposit), response(withdrawal)), result);

            verifyAccountLookup(account);
            verify(transactionRepository).findByAccountIdOrderByCreatedAtDescIdDesc(ACCOUNT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return empty list when the account has no transactions")
        void shouldReturnEmptyListWhenAccountHasNoTransactions() {
            CheckingAccount account = account();

            stubAccountLookup(account);
            when(transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(ACCOUNT_ID)).thenReturn(List.of());

            List<TransactionResponseDTO> result = transactionService.findByAccountNumber(ACCOUNT_NUMBER, ACCOUNT_DIGIT);

            assertEquals(List.of(), result);

            verifyAccountLookup(account);
            verify(transactionRepository).findByAccountIdOrderByCreatedAtDescIdDesc(ACCOUNT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account does not exist")
        void shouldThrowWhenAccountDoesNotExist() {
            when(accountService.findEntityByAccountNumber(ACCOUNT_NUMBER, ACCOUNT_DIGIT))
                    .thenThrow(new AccountNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE));

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ACCOUNT_NOT_FOUND_MESSAGE,
                    () -> transactionService.findByAccountNumber(ACCOUNT_NUMBER, ACCOUNT_DIGIT)
            );

            verify(accountService).findEntityByAccountNumber(ACCOUNT_NUMBER, ACCOUNT_DIGIT);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findByTransactionCode(UUID)")
    class FindByTransactionCode {

        @Test
        @DisplayName("Should return the transaction when code exists")
        void shouldReturnTheTransactionWhenCodeExists() {
            Transaction transaction = new Transaction(TransactionType.DEPOSIT, OPERATION_AMOUNT, CREATED_AT, account());

            when(transactionRepository.findByTransactionCode(transaction.getTransactionCode()))
                    .thenReturn(Optional.of(transaction));

            TransactionResponseDTO result = transactionService.findByTransactionCode(transaction.getTransactionCode());

            assertEquals(response(transaction), result);

            verify(transactionRepository).findByTransactionCode(transaction.getTransactionCode());
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when transaction does not exist")
        void shouldThrowWhenTransactionDoesNotExist() {
            UUID transactionCode = UUID.randomUUID();

            when(transactionRepository.findByTransactionCode(transactionCode)).thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    TransactionNotFoundException.class,
                    TRANSACTION_NOT_FOUND_MESSAGE,
                    () -> transactionService.findByTransactionCode(transactionCode)
            );

            verify(transactionRepository).findByTransactionCode(transactionCode);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("applyYield(String, String)")
    class ApplyYield {

        // eligibleSavingsAccount usa lastYieldDate = hoje - 1 mês: exatamente a borda de elegibilidade.
        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "yieldScenarios")
        @DisplayName("Should apply yield successfully")
        void shouldApplyYieldSuccessfully(
                String scenario,
                String balance,
                String expectedYield,
                String expectedBalance
        ) {
            SavingsAccount account = eligibleSavingsAccount(balance);
            LocalDate today = LocalDate.now();

            stubAccountLookup(account);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            stubSaveReturningArgument();

            TransactionResponseDTO result = transactionService.applyYield(SAVINGS_NUMBER, SAVINGS_DIGIT);

            assertBalance(expectedBalance, account);
            assertFalse(account.getLastYieldDate().isBefore(today));

            ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            verifyAccountLookup(account);
            verify(transactionRepository).existsByAccountIdAndTypeAndCreatedAtBetween(
                    eq(SAVINGS_ID), eq(TransactionType.YIELD), startCaptor.capture(), endCaptor.capture()
            );
            verify(currentUserService).getUsername();
            verify(transactionRepository).save(transactionCaptor.capture());
            verify(eventPublisher).publishEvent(new TransactionOperationEvent(
                    TransactionType.YIELD, SAVINGS_NUMBER, new BigDecimal(expectedYield), USERNAME
            ));
            verifyNoMoreInteractionsOnMocks();

            assertEquals(today.atStartOfDay(), startCaptor.getValue());
            assertEquals(today.atTime(LocalTime.MAX), endCaptor.getValue());
            assertTransaction(transactionCaptor.getValue(), TransactionType.YIELD, new BigDecimal(expectedYield), account);
            assertEquals(response(transactionCaptor.getValue()), result);
        }

        @Test
        @DisplayName("Should throw when account is not a savings account")
        void shouldThrowWhenAccountIsNotSavings() {
            CheckingAccount account = account();

            stubAccountLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotSavingsException.class,
                    NOT_SAVINGS_MESSAGE,
                    () -> transactionService.applyYield(ACCOUNT_NUMBER, ACCOUNT_DIGIT)
            );

            verifyAccountLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when savings account is not active")
        void shouldThrowWhenSavingsAccountIsNotActive() {
            SavingsAccount account = eligibleSavingsAccount("1000.00");
            account.cancel();

            stubAccountLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ACCOUNT_NOT_ACTIVE_MESSAGE,
                    () -> transactionService.applyYield(SAVINGS_NUMBER, SAVINGS_DIGIT)
            );

            assertBalance("1000.00", account);

            verifyAccountLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when yield was already applied today")
        void shouldThrowWhenYieldWasAlreadyAppliedToday() {
            LocalDate lastYieldDate = LocalDate.now().minusMonths(1);
            SavingsAccount account = savingsAccount("1000.00", lastYieldDate);

            stubAccountLookup(account);
            when(transactionRepository.existsByAccountIdAndTypeAndCreatedAtBetween(
                    eq(SAVINGS_ID), eq(TransactionType.YIELD), any(), any()
            )).thenReturn(true);

            assertThrowsWithMessage(
                    YieldAlreadyAppliedException.class,
                    YIELD_ALREADY_APPLIED_MESSAGE,
                    () -> transactionService.applyYield(SAVINGS_NUMBER, SAVINGS_DIGIT)
            );

            assertBalance("1000.00", account);
            assertEquals(lastYieldDate, account.getLastYieldDate());

            verifyAccountLookup(account);
            verifyYieldChecked();
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "ineligibleLastYieldDates")
        @DisplayName("Should throw when account is not eligible for yield")
        void shouldThrowWhenAccountIsNotEligibleForYield(String scenario, LocalDate lastYieldDate) {
            SavingsAccount account = savingsAccount("1000.00", lastYieldDate);

            stubAccountLookup(account);

            assertThrowsWithMessage(
                    YieldNotAvailableException.class,
                    YIELD_NOT_ELIGIBLE_MESSAGE,
                    () -> transactionService.applyYield(SAVINGS_NUMBER, SAVINGS_DIGIT)
            );

            assertBalance("1000.00", account);
            assertEquals(lastYieldDate, account.getLastYieldDate());

            verifyAccountLookup(account);
            verifyYieldChecked();
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "balancesWithoutYield")
        @DisplayName("Should throw when there is no yield available")
        void shouldThrowWhenThereIsNoYieldAvailable(String scenario, String balance) {
            SavingsAccount account = eligibleSavingsAccount(balance);

            stubAccountLookup(account);

            assertThrowsWithMessage(
                    YieldNotAvailableException.class,
                    YIELD_NOT_AVAILABLE_MESSAGE,
                    () -> transactionService.applyYield(SAVINGS_NUMBER, SAVINGS_DIGIT)
            );

            assertBalance(balance, account);

            verifyAccountLookup(account);
            verifyYieldChecked();
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of("amount is null", null),
                Arguments.of("amount is zero", BigDecimal.ZERO),
                Arguments.of("amount is negative", new BigDecimal("-1.00"))
        );
    }

    // Saldo da conta de origem nos testes: 100.00.
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> amountsAboveBalance() {
        return Stream.of(
                Arguments.of("amount exceeds balance", new BigDecimal("200.00")),
                Arguments.of("amount is one cent above balance", new BigDecimal("100.01"))
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> withdrawals() {
        return Stream.of(
                Arguments.of("amount is less than balance", new BigDecimal("30.00"), "70.00"),
                Arguments.of("amount equals balance", new BigDecimal("100.00"), "0.00")
        );
    }

    // Saldos iniciais: origem 100.00, destino 50.00.
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> transfers() {
        return Stream.of(
                Arguments.of("amount is less than source balance", new BigDecimal("30.00"), "70.00", "80.00"),
                Arguments.of("amount equals source balance", new BigDecimal("100.00"), "0.00", "150.00")
        );
    }

    // Taxa de 0,5% com arredondamento HALF_UP em 2 casas.
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> yieldScenarios() {
        return Stream.of(
                Arguments.of("balance 1000.00 yields 5.00", "1000.00", "5.00", "1005.00"),
                Arguments.of("balance 1.00 yields 0.01 (half up)", "1.00", "0.01", "1.01")
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> ineligibleLastYieldDates() {
        return Stream.of(
                Arguments.of("last yield was today", LocalDate.now()),
                Arguments.of("last yield was one day short of a month", LocalDate.now().minusMonths(1).plusDays(1))
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> balancesWithoutYield() {
        return Stream.of(
                Arguments.of("balance is zero", "0.00"),
                Arguments.of("yield rounds down to zero", "0.99")
        );
    }

    private void stubAccountOperationLookup(Account account) {
        when(accountService.findEntityByAccountNumberForUpdate(account.getAccountNumber(), account.getDigit()))
                .thenReturn(account);
    }

    private void stubAccountLookup(Account account) {
        when(accountService.findEntityByAccountNumber(account.getAccountNumber(), account.getDigit()))
                .thenReturn(account);
    }

    private void stubTransferAccountLookup(
            Account source,
            Account destination
    ) {
        when(accountService.lockAccountsForTransfer(
                source.getAccountNumber(),
                source.getDigit(),
                destination.getAccountNumber(),
                destination.getDigit()
        )).thenReturn(
                new LockedAccounts(source, destination)
        );
    }

    private void verifyAccountOperationLookup(Account account) {
        verify(accountService).findEntityByAccountNumberForUpdate(account.getAccountNumber(), account.getDigit());
    }

    private void verifyAccountLookup(Account account) {
        verify(accountService).findEntityByAccountNumber(account.getAccountNumber(), account.getDigit());
    }

    private void verifyTransferAccountLookup(
            Account source,
            Account destination
    ) {
        verify(accountService).lockAccountsForTransfer(
                source.getAccountNumber(),
                source.getDigit(),
                destination.getAccountNumber(),
                destination.getDigit()
        );
    }

    private void stubSaveReturningArgument() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void verifyYieldChecked() {
        verify(transactionRepository).existsByAccountIdAndTypeAndCreatedAtBetween(
                eq(SAVINGS_ID), eq(TransactionType.YIELD), any(), any()
        );
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(transactionRepository, accountService, currentUserService, eventPublisher);
    }

    private static void assertThrowsWithMessage(
            Class<? extends Exception> type,
            String message,
            Executable executable
    ) {
        Exception exception = assertThrows(type, executable);
        assertEquals(message, exception.getMessage());
    }

    private static void assertBalance(String expected, Account account) {
        assertEquals(0, new BigDecimal(expected).compareTo(account.getBalance()));
    }

    private static void assertTransaction(
            Transaction transaction,
            TransactionType type,
            BigDecimal amount,
            Account account
    ) {
        assertEquals(type, transaction.getType());
        assertEquals(0, amount.compareTo(transaction.getAmount()));
        assertSame(account, transaction.getAccount());
    }

    private static TransactionResponseDTO response(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getTransactionCode(),
                transaction.getTransferCode(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt()
        );
    }

    private static AccountOperationRequestDTO operationRequest(BigDecimal amount) {
        return new AccountOperationRequestDTO(ACCOUNT_NUMBER, ACCOUNT_DIGIT, amount);
    }

    private static TransferRequestDTO transferRequest(BigDecimal amount) {
        return new TransferRequestDTO(
                ACCOUNT_NUMBER, ACCOUNT_DIGIT, DESTINATION_NUMBER, DESTINATION_DIGIT, amount
        );
    }

    private static CheckingAccount account() {
        return checkingAccount(ACCOUNT_NUMBER, ACCOUNT_DIGIT, ACCOUNT_ID, "100.00");
    }

    private static CheckingAccount destinationAccount() {
        return checkingAccount(DESTINATION_NUMBER, DESTINATION_DIGIT, DESTINATION_ID, "50.00");
    }

    private static CheckingAccount checkingAccount(String number, String digit, Long id, String balance) {
        // o service não acessa o cliente da conta
        CheckingAccount account = new CheckingAccount(null, number, digit);
        ReflectionTestUtils.setField(account, "id", id);
        account.credit(new BigDecimal(balance));
        return account;
    }

    private static SavingsAccount savingsAccount(String balance, LocalDate lastYieldDate) {
        SavingsAccount account = new SavingsAccount(null, SAVINGS_NUMBER, SAVINGS_DIGIT);
        ReflectionTestUtils.setField(account, "id", SAVINGS_ID);
        ReflectionTestUtils.setField(account, "lastYieldDate", lastYieldDate);
        account.credit(new BigDecimal(balance));
        return account;
    }

    private static SavingsAccount eligibleSavingsAccount(String balance) {
        return savingsAccount(balance, LocalDate.now().minusMonths(1));
    }
}