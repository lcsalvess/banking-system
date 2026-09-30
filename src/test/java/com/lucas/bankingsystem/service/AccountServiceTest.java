package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AccountRequestDTO;
import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.entity.*;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import com.lucas.bankingsystem.entity.enums.AccountType;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.event.account.AccountOperationEvent;
import com.lucas.bankingsystem.event.account.AccountOperationType;
import com.lucas.bankingsystem.exception.account.*;
import com.lucas.bankingsystem.repository.AccountRepository;
import com.lucas.bankingsystem.repository.CheckingAccountRepository;
import com.lucas.bankingsystem.repository.SavingsAccountRepository;
import com.lucas.bankingsystem.service.account.AccountNumberGenerator;
import com.lucas.bankingsystem.service.account.GeneratedAccountNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
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
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AccountService accountService;

    private Client createClient() {
        Address address = new Address("Rua Teste", "123", null, "Centro", "Mogi das Cruzes", State.SP, "08710000");

        Client client = new Client("Cliente Teste", "12345678901", "teste@email.com", "11999999999", address);

        ReflectionTestUtils.setField(client, "id", 1L);
        return client;
    }

    private CheckingAccount createCheckingAccount() {
        Client client = createClient();
        CheckingAccount ca = new CheckingAccount(client, "00001", "5");
        ReflectionTestUtils.setField(ca, "id", 1L);
        return ca;
    }

    private SavingsAccount createSavingsAccount() {
        Client client = createClient();
        SavingsAccount sa = new SavingsAccount(client, "00002", "0");
        ReflectionTestUtils.setField(sa, "id", 2L);
        return sa;
    }

    private AccountRequestDTO createCheckingAccountRequestDTO() {
        return new AccountRequestDTO(1L, AccountType.CHECKING);
    }

    private AccountRequestDTO createSavingsAccountRequestDTO() {
        return new AccountRequestDTO(2L, AccountType.SAVINGS);
    }

    @Nested
    @DisplayName("Ao listar todas as contas")
    class FindAllTests {
        @Test
        @DisplayName("Deve retornar lista de contas quando existirem registros.")
        void shouldReturnListOfAccountsWhenRecordsExist() {
            // Arrange
            CheckingAccount checkingAccount = createCheckingAccount();
            SavingsAccount savingsAccount = createSavingsAccount();
            when(accountRepository.findAll()).thenReturn(List.of(checkingAccount, savingsAccount));
            // Act
            List<AccountResponseDTO> result = accountService.findAll();
            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(checkingAccount.getAccountNumber(), result.getFirst().accountNumber());
            assertEquals(savingsAccount.getAccountNumber(), result.getLast().accountNumber());
            // Verify
            verify(accountRepository).findAll();
        }

        @Test
        @DisplayName("Deve retornar lista vazia quando não existirem contas cadastradas.")
        void shouldReturnEmptyListWhenNoRecordsExist() {
            // Arrange
            when(accountRepository.findAll()).thenReturn(List.of());
            // Act
            List<AccountResponseDTO> result = accountService.findAll();
            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            // Verify
            verify(accountRepository).findAll();
        }
    }

    @Nested
    @DisplayName("Ao buscar entidade conta pelo número da conta")
    class FindEntityByAccountNumberTests {
        @Test
        @DisplayName("Deve retornar conta quando número da conta existir")
        void shouldReturnAccountEntityWhenAccountNumberExists() {
            // Arrange
            CheckingAccount checkingAccount = createCheckingAccount();
            when(accountNumberGenerator.isValid(checkingAccount.getAccountNumber(), checkingAccount.getDigit())).thenReturn(true);
            when(accountRepository.findByAccountNumber(checkingAccount.getAccountNumber())).thenReturn(Optional.of(checkingAccount));
            // Act
            Account result = accountService.findEntityByAccountNumber(checkingAccount.getAccountNumber(), checkingAccount.getDigit());
            // Assert
            assertNotNull(result);
            assertEquals(checkingAccount.getAccountNumber(), result.getAccountNumber());
            assertEquals(checkingAccount.getClient(), result.getClient());
            assertEquals(checkingAccount.getType(), result.getType());
            assertEquals(checkingAccount.getBalance(), result.getBalance());
            // Verify
            verify(accountRepository).findByAccountNumber(checkingAccount.getAccountNumber());
        }

        @Test
        @DisplayName("Deve lançar exceção quando número da conta não existir")
        void shouldThrowExceptionWhenAccountDoesNotExist() {
            // Arrange
            String nonExistentAccountNumber = "99999";
            String nonExistentAccountDigit = "9";
            when(accountNumberGenerator.isValid(nonExistentAccountNumber, nonExistentAccountDigit)).thenReturn(true);
            when(accountRepository.findByAccountNumber(nonExistentAccountNumber)).thenReturn(Optional.empty());
            // Act + Assert
            assertThrows(AccountNotFoundException.class, () -> accountService.findEntityByAccountNumber(nonExistentAccountNumber, nonExistentAccountDigit));
            // Verify
            verify(accountRepository).findByAccountNumber(nonExistentAccountNumber);
        }

        @Test
        @DisplayName("Deve lançar exceção quando o dígito da conta for inválido")
        void shouldThrowExceptionWhenAccountDigitIsInvalid() {
            // Arrange
            CheckingAccount checkingAccount = createCheckingAccount();
            when(accountNumberGenerator.isValid(checkingAccount.getAccountNumber(), "8")).thenReturn(false);
            // Act + Assert
            assertThrows(InvalidAccountDigitException.class, () -> accountService.findEntityByAccountNumber(checkingAccount.getAccountNumber(), "8"));
            // Verify
            verify(accountRepository, never()).findByAccountNumber(any());
        }
    }

    @Nested
    @DisplayName("Ao buscar conta pelo número da conta")
    class FindByAccountNumberTests {
        @Test
        @DisplayName("Deve retornar AccountResponseDTO quando número da conta existir")
        void shouldReturnAccountResponseDTOWhenAccountNumberExists() {
            // Arrange
            SavingsAccount savingsAccount = createSavingsAccount();
            when(accountNumberGenerator.isValid(savingsAccount.getAccountNumber(), savingsAccount.getDigit())).thenReturn(true);
            when(accountRepository.findByAccountNumber(savingsAccount.getAccountNumber())).thenReturn(Optional.of(savingsAccount));
            // Act
            AccountResponseDTO result = accountService.findByAccountNumber(savingsAccount.getAccountNumber(), savingsAccount.getDigit());
            // Assert
            assertNotNull(result);
            assertEquals(savingsAccount.getAccountNumber(), result.accountNumber());
            assertEquals(savingsAccount.getType(), result.type());
            assertEquals(savingsAccount.getClient().getName(), result.clientName());
            // Verify
            verify(accountRepository).findByAccountNumber(savingsAccount.getAccountNumber());
        }

        @Test
        @DisplayName("Deve lançar exceção quando número da conta não existir")
        void shouldThrowExceptionWhenAccountDoesNotExist() {
            // Arrange
            String nonExistentAccountNumber = "99999";
            String nonExistentAccountDigit = "9";
            when(accountNumberGenerator.isValid(nonExistentAccountNumber, nonExistentAccountDigit)).thenReturn(true);
            when(accountRepository.findByAccountNumber(nonExistentAccountNumber)).thenReturn(Optional.empty());
            // Act + Assert
            assertThrows(AccountNotFoundException.class, () -> accountService.findByAccountNumber(nonExistentAccountNumber, nonExistentAccountDigit));
            // Verify
            verify(accountRepository).findByAccountNumber(nonExistentAccountNumber);
        }
    }

    @Nested
    @DisplayName("Ao criar uma conta")
    class CreateTests {
        @Test
        @DisplayName("Deve criar conta corrente com sucesso")
        void shouldCreateCheckingAccountSuccessfully() {
            // Arrange
            AccountRequestDTO dto = createCheckingAccountRequestDTO();
            Client client = createClient();

            when(clientService.findEntityById(dto.clientId())).thenReturn(client);
            when(checkingAccountRepository.existsByClientId(dto.clientId())).thenReturn(false);
            when(accountNumberGenerator.generate()).thenReturn(new GeneratedAccountNumber("00001", "5"));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account savedAccount = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedAccount, "id", 1L);
                return savedAccount;
            });
            // Act
            AccountResponseDTO result = accountService.create(dto);
            // Assert
            assertNotNull(result);
            assertEquals("00001", result.accountNumber());
            assertEquals("5", result.accountDigit());
            assertEquals(client.getName(), result.clientName());
            // Verify
            verify(clientService).findEntityById(dto.clientId());
            verify(checkingAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator).generate();
            verify(accountRepository).save(any(Account.class));
            verify(eventPublisher).publishEvent(
                    new AccountOperationEvent(1L, AccountOperationType.CREATED)
            );
        }

        @Test
        @DisplayName("Deve criar conta poupança com sucesso")
        void shouldCreateSavingsAccountSuccessfully() {
            // Arrange
            AccountRequestDTO dto = createSavingsAccountRequestDTO();
            Client client = createClient();

            when(clientService.findEntityById(dto.clientId())).thenReturn(client);
            when(savingsAccountRepository.existsByClientId(dto.clientId())).thenReturn(false);
            when(accountNumberGenerator.generate()).thenReturn(new GeneratedAccountNumber("00002", "0"));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account savedAccount = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedAccount, "id", 2L);
                return savedAccount;
            });
            // Act
            AccountResponseDTO result = accountService.create(dto);
            // Assert
            assertNotNull(result);
            assertEquals("00002", result.accountNumber());
            assertEquals("0", result.accountDigit());
            assertEquals(AccountType.SAVINGS, result.type());
            assertEquals(client.getName(), result.clientName());
            // Verify
            verify(clientService).findEntityById(dto.clientId());
            verify(savingsAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator).generate();
            verify(accountRepository).save(any(Account.class));
            verify(eventPublisher).publishEvent(
                    new AccountOperationEvent(2L, AccountOperationType.CREATED)
            );
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar criar conta corrente quando cliente já possuir uma")
        void shouldThrowExceptionWhenCreatingCheckingAccountForClientThatAlreadyHasOne() {
            // Arrange
            AccountRequestDTO dto = createCheckingAccountRequestDTO();
            Client client = createClient();

            when(clientService.findEntityById(dto.clientId())).thenReturn(client);
            when(checkingAccountRepository.existsByClientId(dto.clientId())).thenReturn(true);
            // Act + Assert
            assertThrows(AccountAlreadyExistsException.class, () -> accountService.create(dto));
            // Verify
            verify(clientService).findEntityById(dto.clientId());
            verify(checkingAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator, never()).generate();
            verify(accountRepository, never()).save(any(Account.class));
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar criar conta poupança quando cliente já possuir uma")
        void shouldThrowExceptionWhenCreatingSavingsAccountForClientThatAlreadyHasOne() {
            // Arrange
            AccountRequestDTO dto = createSavingsAccountRequestDTO();
            Client client = createClient();

            when(clientService.findEntityById(dto.clientId())).thenReturn(client);
            when(savingsAccountRepository.existsByClientId(dto.clientId())).thenReturn(true);
            // Act + Assert
            assertThrows(AccountAlreadyExistsException.class, () -> accountService.create(dto));
            // Verify
            verify(clientService).findEntityById(dto.clientId());
            verify(savingsAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator, never()).generate();
            verify(accountRepository, never()).save(any(Account.class));
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("Ao cancelar uma conta")
    class CancelAccountTests {
        @Test
        @DisplayName("Deve cancelar conta com sucesso quando ela está ativa e saldo zerado")
        void shouldCancelAccountSuccessfullyWhenActiveAndBalanceIsZero() {
            // Arrange
            CheckingAccount checkingAccount = createCheckingAccount();

            when(accountNumberGenerator.isValid(checkingAccount.getAccountNumber(), checkingAccount.getDigit())).thenReturn(true);
            when(accountRepository.findByAccountNumber(checkingAccount.getAccountNumber())).thenReturn(Optional.of(checkingAccount));
            // Act
            accountService.cancel(checkingAccount.getAccountNumber(), checkingAccount.getDigit());
            // Assert
            assertEquals(AccountStatus.CANCELLED, checkingAccount.getStatus());
            // Verify
            verify(accountRepository).findByAccountNumber(checkingAccount.getAccountNumber());
            verify(eventPublisher).publishEvent(
                    new AccountOperationEvent(1L, AccountOperationType.CANCELLED)
            );
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inexistente")
        void shouldThrowExceptionWhenCancellingNonExistentAccount() {
            // Arrange
            String nonExistentAccountNumber = "99999";
            String nonExistentAccountDigit = "9";

            when(accountNumberGenerator.isValid(nonExistentAccountNumber, nonExistentAccountDigit)).thenReturn(true);
            when(accountRepository.findByAccountNumber(nonExistentAccountNumber)).thenReturn(Optional.empty());
            // Act + Assert
            assertThrows(AccountNotFoundException.class, () -> accountService.cancel(nonExistentAccountNumber, nonExistentAccountDigit));
            // Verify
            verify(accountRepository).findByAccountNumber(nonExistentAccountNumber);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inativa")
        void shouldThrowExceptionWhenCancellingInactiveAccount() {
            // Arrange
            SavingsAccount savingsAccount = createSavingsAccount();
            ReflectionTestUtils.setField(savingsAccount, "status", AccountStatus.CANCELLED);

            when(accountNumberGenerator.isValid(savingsAccount.getAccountNumber(), savingsAccount.getDigit())).thenReturn(true);
            when(accountRepository.findByAccountNumber(savingsAccount.getAccountNumber())).thenReturn(Optional.of(savingsAccount));
            // Act + Assert
            assertThrows(AccountIsNotActiveException.class, () -> accountService.cancel(savingsAccount.getAccountNumber(), savingsAccount.getDigit()));
            // Verify
            verify(accountRepository).findByAccountNumber(savingsAccount.getAccountNumber());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta com saldo")
        void shouldThrowExceptionWhenCancellingAccountWithBalance() {
            // Arrange
            CheckingAccount checkingAccount = createCheckingAccount();
            ReflectionTestUtils.setField(checkingAccount, "balance", BigDecimal.TEN);

            when(accountNumberGenerator.isValid(checkingAccount.getAccountNumber(), checkingAccount.getDigit())).thenReturn(true);
            when(accountRepository.findByAccountNumber(checkingAccount.getAccountNumber())).thenReturn(Optional.of(checkingAccount));
            // Act + Assert
            assertThrows(AccountHasBalanceException.class, () -> accountService.cancel(checkingAccount.getAccountNumber(), checkingAccount.getDigit()));
            // Verify
            verify(accountRepository).findByAccountNumber(checkingAccount.getAccountNumber());
            verifyNoInteractions(eventPublisher);
        }
    }
}
