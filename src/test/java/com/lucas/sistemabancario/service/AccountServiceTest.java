package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.AccountRequestDTO;
import com.lucas.sistemabancario.dto.request.AddressRequestDTO;
import com.lucas.sistemabancario.dto.request.ClientRequestDTO;
import com.lucas.sistemabancario.dto.response.AccountResponseDTO;
import com.lucas.sistemabancario.entity.Account;
import com.lucas.sistemabancario.entity.CheckingAccount;
import com.lucas.sistemabancario.entity.Client;
import com.lucas.sistemabancario.entity.SavingsAccount;
import com.lucas.sistemabancario.entity.enums.AccountStatus;
import com.lucas.sistemabancario.entity.enums.AccountType;
import com.lucas.sistemabancario.entity.enums.State;
import com.lucas.sistemabancario.entity.enums.StreetType;
import com.lucas.sistemabancario.exception.account.AccountAlreadyExistsException;
import com.lucas.sistemabancario.exception.account.AccountHasBalanceException;
import com.lucas.sistemabancario.exception.account.AccountIsNotActiveException;
import com.lucas.sistemabancario.exception.account.AccountNotFoundException;
import com.lucas.sistemabancario.repository.AccountRepository;
import com.lucas.sistemabancario.repository.CheckingAccountRepository;
import com.lucas.sistemabancario.repository.SavingsAccountRepository;
import com.lucas.sistemabancario.service.account.AccountNumberGenerator;
import com.lucas.sistemabancario.service.account.GeneratedAccountNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @InjectMocks
    private AccountService accountService;

    private Client createClient() {
        AddressRequestDTO address = new AddressRequestDTO(
                StreetType.AVENIDA,
                "Teste",
                "123",
                null,                // complement
                "Centro",            // neighborhood
                "Mogi das Cruzes",   // city
                State.SP,            // state
                "12345678"
        );

        ClientRequestDTO dto = new ClientRequestDTO(
                "Cliente Teste",
                "12345678901",
                "teste@email.com",
                "11999999999",
                address
        );

        Client client = new Client(dto);
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
            //Arrange
            CheckingAccount checkingAccount = createCheckingAccount();
            SavingsAccount savingsAccount = createSavingsAccount();
            when(accountRepository.findAll()).thenReturn(List.of(checkingAccount, savingsAccount));
            //Act
            List<AccountResponseDTO> result = accountService.findAll();
            //Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(checkingAccount.getAccountNumber(), result.getFirst().accountNumber());
            assertEquals(savingsAccount.getAccountNumber(), result.getLast().accountNumber());
            //Verify
            verify(accountRepository).findAll();
        }

        @Test
        @DisplayName("Deve retornar lista vazia quando não existirem contas cadastradas.")
        void shouldReturnEmptyListWhenNoRecordsExist() {
            //Arrange
            when(accountRepository.findAll()).thenReturn(List.of());
            //Act
            List<AccountResponseDTO> result = accountService.findAll();
            //Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            //Verify
            verify(accountRepository).findAll();
        }
    }

    @Nested
    @DisplayName("Ao buscar entidade conta pelo número da conta")
    class FindEntityByAccountNumberTests {
        @Test
        @DisplayName("Deve retornar conta quando número da conta existir")
        void shouldReturnAccountEntityWhenAccountNumberExists() {
            //Arrange
            CheckingAccount checkingAccount = createCheckingAccount();
            when(accountRepository
                    .findByAccountNumber(checkingAccount.getAccountNumber()))
                    .thenReturn(Optional.of(checkingAccount));
            //Act
            Account result = accountService.findEntityByAccountNumber(checkingAccount.getAccountNumber());
            //Assert
            assertNotNull(result);
            assertEquals(checkingAccount.getAccountNumber(), result.getAccountNumber());
            assertEquals(checkingAccount.getClient(), result.getClient());
            assertEquals(checkingAccount.getType(), result.getType());
            assertEquals(checkingAccount.getBalance(), result.getBalance());
            //Verify
            verify(accountRepository).findByAccountNumber(checkingAccount.getAccountNumber());
        }

        @Test
        @DisplayName("Deve lançar exceção quando número da conta não existir")
        void shouldThrowExceptionWhenAccountDoesNotExist() {
            //Arrange
            String nonExistentAccountNumber = "99999";
            when(accountRepository.findByAccountNumber(nonExistentAccountNumber)).thenReturn(Optional.empty());
            //Act + Assert
            assertThrows(AccountNotFoundException.class, () -> accountService.findEntityByAccountNumber(nonExistentAccountNumber));
            //Verify
            verify(accountRepository).findByAccountNumber(nonExistentAccountNumber);
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
            when(accountRepository.findByAccountNumber(savingsAccount.getAccountNumber())).thenReturn(Optional.of(savingsAccount));
            // Act
            AccountResponseDTO result = accountService.findByAccountNumber(savingsAccount.getAccountNumber());
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
            //Arrange
            String nonExistentAccountNumber = "99999";
            when(accountRepository.findByAccountNumber(nonExistentAccountNumber)).thenReturn(Optional.empty());
            //Act + Assert
            assertThrows(AccountNotFoundException.class, () -> accountService.findByAccountNumber(nonExistentAccountNumber));
            //Verify
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
            //Verify
            verify(clientService).findEntityById(dto.clientId());
            verify(checkingAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator).generate();
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Deve criar conta poupança com sucesso")
        void shouldCreateSavingsAccountSuccessfully() {
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
            AccountResponseDTO result = accountService.create(dto);
            assertNotNull(result);
            assertEquals("00002", result.accountNumber());
            assertEquals("0", result.accountDigit());
            assertEquals(AccountType.SAVINGS, result.type());
            assertEquals(client.getName(), result.clientName());
            verify(clientService).findEntityById(dto.clientId());
            verify(savingsAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator).generate();
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar criar conta corrente quando cliente já possuir uma")
        void shouldThrowExceptionWhenCreatingCheckingAccountForClientThatAlreadyHasOne() {
            AccountRequestDTO dto = createCheckingAccountRequestDTO();
            Client client = createClient();
            when(clientService.findEntityById(dto.clientId())).thenReturn(client);
            when(checkingAccountRepository.existsByClientId(dto.clientId())).thenReturn(true);
            assertThrows(AccountAlreadyExistsException.class, () -> accountService.create(dto));
            verify(clientService).findEntityById(dto.clientId());
            verify(checkingAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator, never()).generate();
            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar criar conta poupança quando cliente já possuir uma")
        void shouldThrowExceptionWhenCreatingSavingsAccountForClientThatAlreadyHasOne() {
            AccountRequestDTO dto = createSavingsAccountRequestDTO();
            Client client = createClient();
            when(clientService.findEntityById(dto.clientId())).thenReturn(client);
            when(savingsAccountRepository.existsByClientId(dto.clientId())).thenReturn(true);
            assertThrows(AccountAlreadyExistsException.class, () -> accountService.create(dto));
            verify(clientService).findEntityById(dto.clientId());
            verify(savingsAccountRepository).existsByClientId(dto.clientId());
            verify(accountNumberGenerator, never()).generate();
            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("Ao cancelar uma conta")
    class CancelAccountTests {
        @Test
        @DisplayName("Deve cancelar conta com sucesso quando ela está ativa e saldo zerado")
        void shouldCancelAccountSuccessfullyWhenActiveAndBalanceIsZero() {
            CheckingAccount checkingAccount = createCheckingAccount();
            when(accountRepository.findByAccountNumber(checkingAccount.getAccountNumber())).thenReturn(Optional.of(checkingAccount));
            accountService.cancel(checkingAccount.getAccountNumber());
            assertEquals(AccountStatus.CANCELLED, checkingAccount.getStatus());
            verify(accountRepository).findByAccountNumber(checkingAccount.getAccountNumber());
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inexistente")
        void shouldThrowExceptionWhenCancellingNonExistentAccount() {
            String nonExistentAccountNumber = "99999";
            when(accountRepository.findByAccountNumber(nonExistentAccountNumber)).thenReturn(Optional.empty());
            assertThrows(AccountNotFoundException.class, () -> accountService.cancel(nonExistentAccountNumber));
            verify(accountRepository).findByAccountNumber(nonExistentAccountNumber);
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inativa")
        void shouldThrowExceptionWhenCancellingInactiveAccount() {
            SavingsAccount savingsAccount = createSavingsAccount();
            ReflectionTestUtils.setField(savingsAccount, "status", AccountStatus.CANCELLED);
            when(accountRepository.findByAccountNumber(savingsAccount.getAccountNumber())).thenReturn(Optional.of(savingsAccount));
            assertThrows(AccountIsNotActiveException.class, () -> accountService.cancel(savingsAccount.getAccountNumber()));
            verify(accountRepository).findByAccountNumber(savingsAccount.getAccountNumber());
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta com saldo")
        void shouldThrowExceptionWhenCancellingAccountWithBalance() {
            CheckingAccount checkingAccount = createCheckingAccount();
            ReflectionTestUtils.setField(checkingAccount, "balance", BigDecimal.TEN);
            when(accountRepository.findByAccountNumber(checkingAccount.getAccountNumber())).thenReturn(Optional.of(checkingAccount));
            assertThrows(AccountHasBalanceException.class, () -> accountService.cancel(checkingAccount.getAccountNumber()));
            verify(accountRepository).findByAccountNumber(checkingAccount.getAccountNumber());
        }
    }
}
