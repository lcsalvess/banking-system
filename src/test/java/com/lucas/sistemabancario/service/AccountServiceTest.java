package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.ClientRequestDTO;
import com.lucas.sistemabancario.dto.request.AccountRequestDTO;
import com.lucas.sistemabancario.dto.request.AddressRequestDTO;
import com.lucas.sistemabancario.dto.response.AccountResponseDTO;
import com.lucas.sistemabancario.entity.Client;
import com.lucas.sistemabancario.entity.Account;
import com.lucas.sistemabancario.entity.CheckingAccount;
import com.lucas.sistemabancario.entity.SavingsAccount;
import com.lucas.sistemabancario.entity.enums.State;
import com.lucas.sistemabancario.entity.enums.AccountStatus;
import com.lucas.sistemabancario.entity.enums.AccountType;
import com.lucas.sistemabancario.entity.enums.StreetType;
import com.lucas.sistemabancario.exception.account.AccountAlreadyExistsException;
import com.lucas.sistemabancario.exception.account.AccountHasBalanceException;
import com.lucas.sistemabancario.exception.account.AccountIsNotActiveException;
import com.lucas.sistemabancario.exception.account.AccountNotFoundException;
import com.lucas.sistemabancario.repository.CheckingAccountRepository;
import com.lucas.sistemabancario.repository.SavingsAccountRepository;
import com.lucas.sistemabancario.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        CheckingAccount ca = new CheckingAccount(client, "000011");
        ReflectionTestUtils.setField(ca, "id", 1L);
        return ca;
    }

    private SavingsAccount createSavingsAccount() {
        Client client = createClient();
        SavingsAccount sa = new SavingsAccount(client, "000022", LocalDate.now());
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
            CheckingAccount ca = createCheckingAccount();
            SavingsAccount sa = createSavingsAccount();
            when(accountRepository.findAll()).thenReturn(List.of(ca, sa));
            //Act
            List<AccountResponseDTO> result = accountService.findAll();
            //Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(ca.getId(), result.getFirst().id());
            assertEquals(sa.getId(), result.getLast().id());
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
    @DisplayName("Ao buscar entidade conta por ID")
    class FindEntityByIdTests {
        @Test
        @DisplayName("Deve retornar Conta quando ID existir")
        void shouldReturnAccountEntityWhenIdExists() {
            //Arrange
            CheckingAccount ca = createCheckingAccount();
            when(accountRepository.findById(ca.getId())).thenReturn(Optional.of(ca));
            //Act
            Account result = accountService.findEntityById(ca.getId());
            //Assert
            assertNotNull(result);
            assertEquals(ca.getId(), result.getId());
            assertEquals(ca.getAccountNumber(), result.getAccountNumber());
            assertEquals(ca.getClient(), result.getClient());
            assertEquals(ca.getType(), result.getType());
            assertEquals(ca.getBalance(), result.getBalance());
            //Verify
            verify(accountRepository).findById(ca.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando ID da conta não existir")
        void shouldThrowExceptionWhenAccountDoesNotExist() {
            //Arrange
            Long nonExistentId = 99L;
            when(accountRepository.findById(nonExistentId)).thenReturn(Optional.empty());
            //Act + Assert
            assertThrows(AccountNotFoundException.class, () -> accountService.findEntityById(nonExistentId));
            //Verify
            verify(accountRepository).findById(nonExistentId);
        }
    }

    @Nested
    @DisplayName("Ao buscar conta por ID")
    class FindByIdTests {
        @Test
        @DisplayName("Deve retornar ContaResponseDTO quando ID existir")
        void shouldReturnAccountResponseDTOWhenIdExists() {
            // Arrange
            SavingsAccount sa = createSavingsAccount();
            when(accountRepository.findById(sa.getId())).thenReturn(Optional.of(sa));
            // Act
            AccountResponseDTO result = accountService.findById(sa.getId());
            // Assert
            assertNotNull(result);
            assertEquals(sa.getId(), result.id());
            assertEquals(sa.getAccountNumber(), result.accountNumber());
            assertEquals(sa.getType(), result.accountType());
            assertEquals(sa.getClient().getName(), result.clientName());
            // Verify
            verify(accountRepository).findById(sa.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando ID da conta não existir")
        void shouldThrowExceptionWhenAccountDoesNotExist() {
            //Arrange
            Long nonExistentId = 99L;
            when(accountRepository.findById(nonExistentId)).thenReturn(Optional.empty());
            //Act + Assert
            assertThrows(AccountNotFoundException.class, () -> accountService.findById(nonExistentId));
            //Verify
            verify(accountRepository).findById(nonExistentId);
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
            when(accountRepository.getNextAccountNumber()).thenReturn(1L);
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account savedAccount = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedAccount, "id", 1L);
                return savedAccount;
            });
            // Act
            AccountResponseDTO result = accountService.create(dto);
            // Assert
            assertNotNull(result);
            assertEquals(1L, result.id());
            assertEquals("000011", result.accountNumber());
            assertEquals(AccountType.CHECKING, result.accountType());
            assertEquals(client.getName(), result.clientName());
            //Verify
            verify(clientService).findEntityById(dto.clientId());
            verify(checkingAccountRepository).existsByClientId(dto.clientId());
            verify(accountRepository).getNextAccountNumber();
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Deve criar conta poupança com sucesso")
        void shouldCreateSavingsAccountSuccessfully() {
            AccountRequestDTO dto = createSavingsAccountRequestDTO();
            Client client = createClient();
            when(clientService.findEntityById(dto.clientId())).thenReturn(client);
            when(savingsAccountRepository.existsByClientId(dto.clientId())).thenReturn(false);
            when(accountRepository.getNextAccountNumber()).thenReturn(2L);
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account savedAccount = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedAccount, "id", 2L);
                return savedAccount;
            });
            AccountResponseDTO result = accountService.create(dto);
            assertNotNull(result);
            assertEquals(2L, result.id());
            assertEquals("000022", result.accountNumber());
            assertEquals(AccountType.SAVINGS, result.accountType());
            assertEquals(client.getName(), result.clientName());
            verify(clientService).findEntityById(dto.clientId());
            verify(savingsAccountRepository).existsByClientId(dto.clientId());
            verify(accountRepository).getNextAccountNumber();
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
            verify(accountRepository, never()).getNextAccountNumber();
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
            verify(accountRepository, never()).getNextAccountNumber();
            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("Ao cancelar uma conta")
    class CancelAccountTests {
        @Test
        @DisplayName("Deve cancelar conta com sucesso quando ela está ativa e saldo zerado")
        void shouldCancelAccountSuccessfullyWhenActiveAndBalanceIsZero() {
            CheckingAccount ca = createCheckingAccount();
            when(accountRepository.findById(ca.getId())).thenReturn(Optional.of(ca));
            accountService.cancel(ca.getId());
            assertEquals(AccountStatus.CANCELLED, ca.getStatus());
            verify(accountRepository).findById(ca.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inexistente")
        void shouldThrowExceptionWhenCancellingNonExistentAccount() {
            Long nonExistentId = 99L;
            when(accountRepository.findById(nonExistentId)).thenReturn(Optional.empty());
            assertThrows(AccountNotFoundException.class, () -> accountService.cancel(nonExistentId));
            verify(accountRepository).findById(nonExistentId);
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inativa")
        void shouldThrowExceptionWhenCancellingInactiveAccount() {
            SavingsAccount sa = createSavingsAccount();
            ReflectionTestUtils.setField(sa, "status", AccountStatus.CANCELLED);
            when(accountRepository.findById(sa.getId())).thenReturn(Optional.of(sa));
            assertThrows(AccountIsNotActiveException.class, () -> accountService.cancel(sa.getId()));
            verify(accountRepository).findById(sa.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta com saldo")
        void shouldThrowExceptionWhenCancellingAccountWithBalance() {
            CheckingAccount ca = createCheckingAccount();
            ReflectionTestUtils.setField(ca, "balance", BigDecimal.TEN);
            when(accountRepository.findById(ca.getId())).thenReturn(Optional.of(ca));
            assertThrows(AccountHasBalanceException.class, () -> accountService.cancel(ca.getId()));
            verify(accountRepository).findById(ca.getId());
        }
    }
}
