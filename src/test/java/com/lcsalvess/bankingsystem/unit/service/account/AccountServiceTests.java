package com.lcsalvess.bankingsystem.unit.service.account;

import com.lcsalvess.bankingsystem.dto.request.AccountRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.AccountResponseDTO;
import com.lcsalvess.bankingsystem.entity.*;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.entity.enums.AccountType;
import com.lcsalvess.bankingsystem.entity.enums.State;
import com.lcsalvess.bankingsystem.event.account.AccountOperationEvent;
import com.lcsalvess.bankingsystem.event.account.AccountOperationType;
import com.lcsalvess.bankingsystem.exception.account.*;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.service.account.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.GeneratedAccountNumber;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.hibernate.exception.ConstraintViolationException;
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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTests {

    private static final String PROVIDER = "com.lcsalvess.bankingsystem.unit.service.account.AccountServiceTests#";

    private static final String USERNAME = "usuario.teste";
    private static final Long CLIENT_ID = 1L;
    private static final String CLIENT_NAME = "Cliente Teste";
    private static final Long SAVED_ACCOUNT_ID = 10L;

    private static final String CHECKING_NUMBER = "00001";
    private static final String CHECKING_DIGIT = "9";
    private static final String SAVINGS_NUMBER = "00002";
    private static final String SAVINGS_DIGIT = "7";
    private static final String INVALID_DIGIT = "8";
    private static final String NON_EXISTENT_NUMBER = "99999";
    private static final String NON_EXISTENT_DIGIT = "7";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientService clientService;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AccountService accountService;

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("Should return all accounts successfully")
        void shouldReturnAllAccountsSuccessfully() {
            when(accountRepository.findAllWithClient())
                    .thenReturn(List.of(checkingAccount(), savingsAccount()));

            List<AccountResponseDTO> result = accountService.findAll();

            assertEquals(
                    List.of(
                            accountResponse(CHECKING_NUMBER, CHECKING_DIGIT, AccountType.CHECKING),
                            accountResponse(SAVINGS_NUMBER, SAVINGS_DIGIT, AccountType.SAVINGS)
                    ),
                    result
            );

            verify(accountRepository).findAllWithClient();
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return empty list when there are no accounts")
        void shouldReturnEmptyListWhenThereAreNoAccounts() {
            when(accountRepository.findAllWithClient()).thenReturn(List.of());

            List<AccountResponseDTO> result = accountService.findAll();

            assertEquals(List.of(), result);

            verify(accountRepository).findAllWithClient();
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findEntityByAccountNumber(String, String)")
    class FindEntityByAccountNumber {

        @Test
        @DisplayName("Should return the account entity when account number exists")
        void shouldReturnAccountEntityWhenAccountNumberExists() {
            CheckingAccount account = checkingAccount();
            stubAccountLookup(account);

            Account result = accountService.findEntityByAccountNumber(CHECKING_NUMBER, CHECKING_DIGIT);

            assertSame(account, result);

            verifyAccountLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account does not exist")
        void shouldThrowWhenAccountDoesNotExist() {
            stubAccountNotFound();

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ApiErrorMessages.ACCOUNT_NOT_FOUND,
                    () -> accountService.findEntityByAccountNumber(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT)
            );

            verifyAccountNotFoundLookup();
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account digit is invalid")
        void shouldThrowWhenAccountDigitIsInvalid() {
            when(accountNumberGenerator.isValid(CHECKING_NUMBER, INVALID_DIGIT)).thenReturn(false);

            assertThrowsWithMessage(
                    InvalidAccountDigitException.class,
                    ApiErrorMessages.INVALID_ACCOUNT_DIGIT,
                    () -> accountService.findEntityByAccountNumber(CHECKING_NUMBER, INVALID_DIGIT)
            );

            verify(accountNumberGenerator).isValid(CHECKING_NUMBER, INVALID_DIGIT);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findByAccountNumber(String, String)")
    class FindByAccountNumber {

        @Test
        @DisplayName("Should return the account response when account number exists")
        void shouldReturnAccountResponseWhenAccountNumberExists() {
            when(accountNumberGenerator.isValid(SAVINGS_NUMBER, SAVINGS_DIGIT)).thenReturn(true);
            when(accountRepository.findByAccountNumberWithClient(SAVINGS_NUMBER))
                    .thenReturn(Optional.of(savingsAccount()));

            AccountResponseDTO result = accountService.findByAccountNumber(SAVINGS_NUMBER, SAVINGS_DIGIT);

            assertEquals(accountResponse(SAVINGS_NUMBER, SAVINGS_DIGIT, AccountType.SAVINGS), result);

            verify(accountNumberGenerator).isValid(SAVINGS_NUMBER, SAVINGS_DIGIT);
            verify(accountRepository).findByAccountNumberWithClient(SAVINGS_NUMBER);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account does not exist")
        void shouldThrowWhenAccountDoesNotExist() {
            when(accountNumberGenerator.isValid(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT)).thenReturn(true);
            when(accountRepository.findByAccountNumberWithClient(NON_EXISTENT_NUMBER))
                    .thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ApiErrorMessages.ACCOUNT_NOT_FOUND,
                    () -> accountService.findByAccountNumber(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT)
            );

            verify(accountNumberGenerator).isValid(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT);
            verify(accountRepository).findByAccountNumberWithClient(NON_EXISTENT_NUMBER);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account digit is invalid")
        void shouldThrowWhenAccountDigitIsInvalid() {
            when(accountNumberGenerator.isValid(SAVINGS_NUMBER, INVALID_DIGIT)).thenReturn(false);

            assertThrowsWithMessage(
                    InvalidAccountDigitException.class,
                    ApiErrorMessages.INVALID_ACCOUNT_DIGIT,
                    () -> accountService.findByAccountNumber(SAVINGS_NUMBER, INVALID_DIGIT)
            );

            verify(accountNumberGenerator).isValid(SAVINGS_NUMBER, INVALID_DIGIT);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("create(AccountRequestDTO)")
    class Create {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "accountTypes")
        @DisplayName("Should create account successfully")
        void shouldCreateAccountSuccessfully(
                String scenario,
                AccountType type,
                Class<? extends Account> expectedClass,
                String number,
                String digit
        ) {
            AccountRequestDTO request = new AccountRequestDTO(CLIENT_ID, type);
            Client client = client();

            when(clientService.findEntityById(CLIENT_ID)).thenReturn(client);
            when(accountNumberGenerator.generate()).thenReturn(new GeneratedAccountNumber(number, digit));
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> {
                Account savedAccount = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedAccount, "id", SAVED_ACCOUNT_ID);
                return savedAccount;
            });

            AccountResponseDTO result = accountService.create(request);

            assertEquals(accountResponse(number, digit, type), result);

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

            verify(clientService).findEntityById(CLIENT_ID);
            verify(accountNumberGenerator).generate();
            verify(currentUserService).getUsername();
            verify(accountRepository).saveAndFlush(accountCaptor.capture());
            verify(eventPublisher).publishEvent(
                    new AccountOperationEvent(SAVED_ACCOUNT_ID, AccountOperationType.CREATED, USERNAME)
            );
            verifyNoMoreInteractionsOnMocks();

            assertInstanceOf(expectedClass, accountCaptor.getValue());
            assertSame(client, accountCaptor.getValue().getClient());
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            AccountRequestDTO request = new AccountRequestDTO(CLIENT_ID, AccountType.CHECKING);

            when(clientService.findEntityById(CLIENT_ID))
                    .thenThrow(new ClientNotFoundException());

            assertThrowsWithMessage(
                    ClientNotFoundException.class,
                    ApiErrorMessages.CLIENT_NOT_FOUND,
                    () -> accountService.create(request)
            );

            verify(clientService).findEntityById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "databaseDuplicateScenarios")
        @DisplayName("Should throw when database rejects duplicate active account")
        void shouldThrowWhenDatabaseRejectsDuplicateActiveAccount(
                String scenario,
                AccountType type,
                String number,
                String digit,
                String message
        ) {
            AccountRequestDTO request = new AccountRequestDTO(CLIENT_ID, type);

            when(clientService.findEntityById(CLIENT_ID)).thenReturn(client());
            when(accountNumberGenerator.generate())
                    .thenReturn(new GeneratedAccountNumber(number, digit));
            when(currentUserService.getUsername()).thenReturn(USERNAME);

            ConstraintViolationException cause = new ConstraintViolationException(
                    "Unique index violation",
                    null,
                    DatabaseConstraint.ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE.getConstraintName()
            );

            when(accountRepository.saveAndFlush(any(Account.class)))
                    .thenThrow(new DataIntegrityViolationException(
                            "Could not execute statement",
                            cause
                    ));

            assertThrowsWithMessage(
                    AccountAlreadyExistsException.class,
                    message,
                    () -> accountService.create(request)
            );

            verify(clientService).findEntityById(CLIENT_ID);
            verify(accountNumberGenerator).generate();
            verify(currentUserService).getUsername();
            verify(accountRepository).saveAndFlush(any(Account.class));
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "unrelatedDataIntegrityViolations")
        @DisplayName("Should propagate unrelated data integrity violations")
        void shouldPropagateUnrelatedDataIntegrityViolation(
                String scenario,
                DataIntegrityViolationException exception
        ) {
            AccountRequestDTO request =
                    new AccountRequestDTO(CLIENT_ID, AccountType.CHECKING);

            when(clientService.findEntityById(CLIENT_ID)).thenReturn(client());
            when(accountNumberGenerator.generate())
                    .thenReturn(new GeneratedAccountNumber(CHECKING_NUMBER, CHECKING_DIGIT));
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(accountRepository.saveAndFlush(any(Account.class)))
                    .thenThrow(exception);

            DataIntegrityViolationException thrown = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> accountService.create(request)
            );

            assertSame(exception, thrown);

            verify(clientService).findEntityById(CLIENT_ID);
            verify(accountNumberGenerator).generate();
            verify(currentUserService).getUsername();
            verify(accountRepository).saveAndFlush(any(Account.class));
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("cancel(String, String)")
    class Cancel {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "cancellableAccounts")
        @DisplayName("Should cancel account successfully")
        void shouldCancelAccountSuccessfully(String scenario, Account account) {
            stubAccountLookupForUpdate(account);
            when(currentUserService.getUsername()).thenReturn(USERNAME);

            accountService.cancel(account.getAccountNumber(), account.getDigit());

            assertEquals(AccountStatus.CANCELLED, account.getStatus());

            verifyAccountLookupForUpdate(account);
            verify(currentUserService).getUsername();
            verify(eventPublisher).publishEvent(
                    new AccountOperationEvent(account.getId(), AccountOperationType.CANCELLED, USERNAME)
            );
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "accountsWithBalance")
        @DisplayName("Should throw when account has balance")
        void shouldThrowWhenAccountHasBalance(String scenario, BigDecimal balance) {
            Account account = checkingAccount();
            account.credit(balance);
            stubAccountLookupForUpdate(account);

            assertThrowsWithMessage(
                    AccountHasBalanceException.class,
                    ApiErrorMessages.ACCOUNT_HAS_BALANCE,
                    () -> accountService.cancel(account.getAccountNumber(), account.getDigit())
            );

            verifyAccountLookupForUpdate(account);
            verifyNoMoreInteractionsOnMocks();
        }

        // A validação de conta ativa vem antes da de saldo: conta cancelada com saldo
        // deve falhar por "não está ativa".
        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "cancelledAccountBalances")
        @DisplayName("Should throw when account is not active")
        void shouldThrowWhenAccountIsNotActive(String scenario, BigDecimal balance) {
            Account account = checkingAccount();
            account.credit(balance);
            account.cancel();
            stubAccountLookupForUpdate(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE,
                    () -> accountService.cancel(account.getAccountNumber(), account.getDigit())
            );

            verifyAccountLookupForUpdate(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account does not exist")
        void shouldThrowWhenAccountDoesNotExist() {
            stubAccountNotFoundForUpdate();

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ApiErrorMessages.ACCOUNT_NOT_FOUND,
                    () -> accountService.cancel(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT)
            );

            verifyAccountNotFoundLookupForUpdate();
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account digit is invalid")
        void shouldThrowWhenAccountDigitIsInvalid() {
            when(accountNumberGenerator.isValid(CHECKING_NUMBER, INVALID_DIGIT)).thenReturn(false);

            assertThrowsWithMessage(
                    InvalidAccountDigitException.class,
                    ApiErrorMessages.INVALID_ACCOUNT_DIGIT,
                    () -> accountService.cancel(CHECKING_NUMBER, INVALID_DIGIT)
            );

            verify(accountNumberGenerator).isValid(CHECKING_NUMBER, INVALID_DIGIT);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("transaction boundaries")
    class TransactionBoundaries {

        // Consultas devem ser read-only; create e cancel precisam de transação de escrita.
        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "transactionModes")
        @DisplayName("Should declare the expected transaction mode")
        void shouldDeclareExpectedTransactionMode(
                String scenario,
                String methodName,
                Class<?>[] parameterTypes,
                boolean readOnly
        ) throws NoSuchMethodException {
            Method method = AccountService.class.getMethod(methodName, parameterTypes);

            Transactional transactional =
                    AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

            assertNotNull(transactional);
            assertEquals(readOnly, transactional.readOnly());
        }
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> accountTypes() {
        return Stream.of(
                Arguments.of("checking account", AccountType.CHECKING, CheckingAccount.class,
                        CHECKING_NUMBER, CHECKING_DIGIT),
                Arguments.of("savings account", AccountType.SAVINGS, SavingsAccount.class,
                        SAVINGS_NUMBER, SAVINGS_DIGIT)
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> databaseDuplicateScenarios() {
        return Stream.of(
                Arguments.of("database rejects duplicate checking account", AccountType.CHECKING,
                        CHECKING_NUMBER, CHECKING_DIGIT, ApiErrorMessages.CHECKING_ACCOUNT_ALREADY_EXISTS),
                Arguments.of("database rejects duplicate savings account", AccountType.SAVINGS,
                        SAVINGS_NUMBER, SAVINGS_DIGIT, ApiErrorMessages.SAVINGS_ACCOUNT_ALREADY_EXISTS)
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> unrelatedDataIntegrityViolations() {
        return Stream.of(
                Arguments.of("another constraint is violated",
                        new DataIntegrityViolationException(
                                "Database error",
                                new ConstraintViolationException(
                                        "Other constraint violation",
                                        null,
                                        "uk_other_constraint"
                                )
                        )),
                Arguments.of("no constraint violation in the cause chain",
                        new DataIntegrityViolationException("Database error"))
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> transactionModes() {
        return Stream.of(
                Arguments.of("findAll is read-only", "findAll",
                        new Class<?>[]{}, true),
                Arguments.of("findEntityByAccountNumber is read-only", "findEntityByAccountNumber",
                        new Class<?>[]{String.class, String.class}, true),
                Arguments.of("findByAccountNumber is read-only", "findByAccountNumber",
                        new Class<?>[]{String.class, String.class}, true),
                Arguments.of("create is a write transaction", "create",
                        new Class<?>[]{AccountRequestDTO.class}, false),
                Arguments.of("cancel is a write transaction", "cancel",
                        new Class<?>[]{String.class, String.class}, false)
        );
    }

    // "0.00" usa escala diferente de BigDecimal.ZERO: o serviço compara com compareTo, não equals.
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> cancellableAccounts() {
        return Stream.of(
                Arguments.of("checking account", checkingAccount()),
                Arguments.of("savings account", savingsAccount()),
                Arguments.of("checking account with balance 0.00", withBalance(checkingAccount(), "0.00"))
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> accountsWithBalance() {
        return Stream.of(
                Arguments.of("positive balance", new BigDecimal("10.00")),
                Arguments.of("smallest positive balance", new BigDecimal("0.01")),
                Arguments.of("negative balance", new BigDecimal("-5.00"))
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> cancelledAccountBalances() {
        return Stream.of(
                Arguments.of("cancelled account without balance", BigDecimal.ZERO),
                Arguments.of("cancelled account with balance", BigDecimal.TEN)
        );
    }

    private void stubAccountLookup(Account account) {
        when(accountNumberGenerator.isValid(account.getAccountNumber(), account.getDigit()))
                .thenReturn(true);
        when(accountRepository.findByAccountNumber(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
    }

    private void stubAccountLookupForUpdate(Account account) {
        when(accountNumberGenerator.isValid(account.getAccountNumber(), account.getDigit()))
                .thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
    }

    private void verifyAccountLookup(Account account) {
        verify(accountNumberGenerator)
                .isValid(account.getAccountNumber(), account.getDigit());
        verify(accountRepository)
                .findByAccountNumber(account.getAccountNumber());
    }

    private void verifyAccountLookupForUpdate(Account account) {
        verify(accountNumberGenerator)
                .isValid(account.getAccountNumber(), account.getDigit());
        verify(accountRepository)
                .findByAccountNumberForUpdate(account.getAccountNumber());
    }

    private void stubAccountNotFound() {
        when(accountNumberGenerator.isValid(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT))
                .thenReturn(true);
        when(accountRepository.findByAccountNumber(NON_EXISTENT_NUMBER))
                .thenReturn(Optional.empty());
    }

    private void stubAccountNotFoundForUpdate() {
        when(accountNumberGenerator.isValid(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT))
                .thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate(NON_EXISTENT_NUMBER))
                .thenReturn(Optional.empty());
    }

    private void verifyAccountNotFoundLookup() {
        verify(accountNumberGenerator).isValid(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT);
        verify(accountRepository).findByAccountNumber(NON_EXISTENT_NUMBER);
    }

    private void verifyAccountNotFoundLookupForUpdate() {
        verify(accountNumberGenerator).isValid(
                NON_EXISTENT_NUMBER,
                NON_EXISTENT_DIGIT
        );

        verify(accountRepository).findByAccountNumberForUpdate(
                NON_EXISTENT_NUMBER
        );
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(
                accountRepository,
                clientService,
                accountNumberGenerator,
                currentUserService,
                eventPublisher
        );
    }

    private static void assertThrowsWithMessage(
            Class<? extends Exception> type,
            String message,
            Executable executable
    ) {
        Exception exception = assertThrows(type, executable);
        assertEquals(message, exception.getMessage());
    }

    private static Client client() {
        Address address = new Address(
                "Rua Teste", "123", null, "Centro", "Mogi das Cruzes", State.SP, "08710000"
        );
        Client client = new Client(CLIENT_NAME, "52998224725", "teste@email.com", "11999999999", address);
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        return client;
    }

    private static CheckingAccount checkingAccount() {
        CheckingAccount account = new CheckingAccount(client(), CHECKING_NUMBER, CHECKING_DIGIT);
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }

    private static SavingsAccount savingsAccount() {
        SavingsAccount account = new SavingsAccount(client(), SAVINGS_NUMBER, SAVINGS_DIGIT);
        ReflectionTestUtils.setField(account, "id", 2L);
        return account;
    }

    private static <T extends Account> T withBalance(T account, String balance) {
        account.credit(new BigDecimal(balance));
        return account;
    }

    private static AccountResponseDTO accountResponse(String number, String digit, AccountType type) {
        return new AccountResponseDTO(
                number, digit, CLIENT_NAME, BigDecimal.ZERO, type, AccountStatus.ACTIVE
        );
    }
}