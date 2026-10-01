package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AccountRequestDTO;
import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.entity.Account;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.CheckingAccount;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.SavingsAccount;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import com.lucas.bankingsystem.entity.enums.AccountType;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.event.account.AccountOperationEvent;
import com.lucas.bankingsystem.event.account.AccountOperationType;
import com.lucas.bankingsystem.exception.account.AccountAlreadyExistsException;
import com.lucas.bankingsystem.exception.account.AccountHasBalanceException;
import com.lucas.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lucas.bankingsystem.exception.account.AccountNotFoundException;
import com.lucas.bankingsystem.exception.account.InvalidAccountDigitException;
import com.lucas.bankingsystem.exception.account.InvalidAccountTypeException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.AccountRepository;
import com.lucas.bankingsystem.repository.CheckingAccountRepository;
import com.lucas.bankingsystem.repository.SavingsAccountRepository;
import com.lucas.bankingsystem.service.account.AccountNumberGenerator;
import com.lucas.bankingsystem.service.account.GeneratedAccountNumber;
import com.lucas.bankingsystem.service.security.CurrentUserService;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    private static final String PROVIDER = "com.lucas.bankingsystem.service.AccountServiceTest#";

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

    private static final String ACCOUNT_NOT_FOUND_MESSAGE = "Conta não encontrada.";
    private static final String INVALID_DIGIT_MESSAGE = "Dígito da conta inválido.";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Cliente não encontrado.";
    private static final String CHECKING_ALREADY_EXISTS_MESSAGE = "O cliente já possui uma conta corrente.";
    private static final String SAVINGS_ALREADY_EXISTS_MESSAGE = "O cliente já possui uma conta poupança.";
    private static final String INVALID_ACCOUNT_TYPE_MESSAGE = "Tipo de conta inválido.";
    private static final String ACCOUNT_HAS_BALANCE_MESSAGE = "Não é possível cancelar uma conta com saldo.";
    private static final String ACCOUNT_NOT_ACTIVE_MESSAGE = "Não é possível cancelar uma conta que não está ativa.";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CheckingAccountRepository checkingAccountRepository;

    @Mock
    private SavingsAccountRepository savingsAccountRepository;

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
                    ACCOUNT_NOT_FOUND_MESSAGE,
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
                    INVALID_DIGIT_MESSAGE,
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
                    ACCOUNT_NOT_FOUND_MESSAGE,
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
                    INVALID_DIGIT_MESSAGE,
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
            stubActiveAccountExists(type, false);
            when(accountNumberGenerator.generate()).thenReturn(new GeneratedAccountNumber(number, digit));
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account savedAccount = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedAccount, "id", SAVED_ACCOUNT_ID);
                return savedAccount;
            });

            AccountResponseDTO result = accountService.create(request);

            assertEquals(accountResponse(number, digit, type), result);

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

            verify(clientService).findEntityById(CLIENT_ID);
            verifyActiveAccountChecked(type);
            verify(accountNumberGenerator).generate();
            verify(currentUserService).getUsername();
            verify(accountRepository).save(accountCaptor.capture());
            verify(eventPublisher).publishEvent(
                    new AccountOperationEvent(SAVED_ACCOUNT_ID, AccountOperationType.CREATED, USERNAME)
            );
            verifyNoMoreInteractionsOnMocks();

            assertInstanceOf(expectedClass, accountCaptor.getValue());
            assertSame(client, accountCaptor.getValue().getClient());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "duplicateAccountScenarios")
        @DisplayName("Should throw when client already has an active account of the same type")
        void shouldThrowWhenClientAlreadyHasActiveAccountOfSameType(
                String scenario,
                AccountType type,
                String message
        ) {
            AccountRequestDTO request = new AccountRequestDTO(CLIENT_ID, type);

            when(clientService.findEntityById(CLIENT_ID)).thenReturn(client());
            stubActiveAccountExists(type, true);

            assertThrowsWithMessage(
                    AccountAlreadyExistsException.class,
                    message,
                    () -> accountService.create(request)
            );

            verify(clientService).findEntityById(CLIENT_ID);
            verifyActiveAccountChecked(type);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            AccountRequestDTO request = new AccountRequestDTO(CLIENT_ID, AccountType.CHECKING);

            when(clientService.findEntityById(CLIENT_ID))
                    .thenThrow(new ClientNotFoundException(CLIENT_NOT_FOUND_MESSAGE));

            assertThrowsWithMessage(
                    ClientNotFoundException.class,
                    CLIENT_NOT_FOUND_MESSAGE,
                    () -> accountService.create(request)
            );

            verify(clientService).findEntityById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account type is null")
        void shouldThrowWhenAccountTypeIsNull() {
            AccountRequestDTO request = new AccountRequestDTO(CLIENT_ID, null);

            when(clientService.findEntityById(CLIENT_ID)).thenReturn(client());

            assertThrowsWithMessage(
                    InvalidAccountTypeException.class,
                    INVALID_ACCOUNT_TYPE_MESSAGE,
                    () -> accountService.create(request)
            );

            verify(clientService).findEntityById(CLIENT_ID);
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
            stubAccountLookup(account);
            when(currentUserService.getUsername()).thenReturn(USERNAME);

            accountService.cancel(account.getAccountNumber(), account.getDigit());

            assertEquals(AccountStatus.CANCELLED, account.getStatus());

            verifyAccountLookup(account);
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
            stubAccountLookup(account);

            assertThrowsWithMessage(
                    AccountHasBalanceException.class,
                    ACCOUNT_HAS_BALANCE_MESSAGE,
                    () -> accountService.cancel(account.getAccountNumber(), account.getDigit())
            );

            verifyAccountLookup(account);
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
            stubAccountLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ACCOUNT_NOT_ACTIVE_MESSAGE,
                    () -> accountService.cancel(account.getAccountNumber(), account.getDigit())
            );

            verifyAccountLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account does not exist")
        void shouldThrowWhenAccountDoesNotExist() {
            stubAccountNotFound();

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ACCOUNT_NOT_FOUND_MESSAGE,
                    () -> accountService.cancel(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT)
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
                    INVALID_DIGIT_MESSAGE,
                    () -> accountService.cancel(CHECKING_NUMBER, INVALID_DIGIT)
            );

            verify(accountNumberGenerator).isValid(CHECKING_NUMBER, INVALID_DIGIT);
            verifyNoMoreInteractionsOnMocks();
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
    static Stream<Arguments> duplicateAccountScenarios() {
        return Stream.of(
                Arguments.of("client already has a checking account",
                        AccountType.CHECKING, CHECKING_ALREADY_EXISTS_MESSAGE),
                Arguments.of("client already has a savings account",
                        AccountType.SAVINGS, SAVINGS_ALREADY_EXISTS_MESSAGE)
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
        when(accountNumberGenerator.isValid(account.getAccountNumber(), account.getDigit())).thenReturn(true);
        when(accountRepository.findByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));
    }

    private void verifyAccountLookup(Account account) {
        verify(accountNumberGenerator).isValid(account.getAccountNumber(), account.getDigit());
        verify(accountRepository).findByAccountNumber(account.getAccountNumber());
    }

    private void stubAccountNotFound() {
        when(accountNumberGenerator.isValid(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT)).thenReturn(true);
        when(accountRepository.findByAccountNumber(NON_EXISTENT_NUMBER)).thenReturn(Optional.empty());
    }

    private void verifyAccountNotFoundLookup() {
        verify(accountNumberGenerator).isValid(NON_EXISTENT_NUMBER, NON_EXISTENT_DIGIT);
        verify(accountRepository).findByAccountNumber(NON_EXISTENT_NUMBER);
    }

    private void stubActiveAccountExists(AccountType type, boolean exists) {
        switch (type) {
            case CHECKING ->
                    when(checkingAccountRepository.existsByClientIdAndStatus(
                            CLIENT_ID, AccountStatus.ACTIVE
                    )).thenReturn(exists);

            case SAVINGS ->
                    when(savingsAccountRepository.existsByClientIdAndStatus(
                            CLIENT_ID, AccountStatus.ACTIVE
                    )).thenReturn(exists);
        }
    }

    private void verifyActiveAccountChecked(AccountType type) {
        switch (type) {
            case CHECKING ->
                    verify(checkingAccountRepository)
                            .existsByClientIdAndStatus(CLIENT_ID, AccountStatus.ACTIVE);

            case SAVINGS ->
                    verify(savingsAccountRepository)
                            .existsByClientIdAndStatus(CLIENT_ID, AccountStatus.ACTIVE);
        }
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(
                accountRepository,
                checkingAccountRepository,
                savingsAccountRepository,
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