package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.AccountRequestDTO;
import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import com.lucas.bankingsystem.entity.enums.AccountType;
import com.lucas.bankingsystem.exception.account.AccountAlreadyExistsException;
import com.lucas.bankingsystem.exception.account.AccountHasBalanceException;
import com.lucas.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lucas.bankingsystem.exception.account.AccountNotFoundException;
import com.lucas.bankingsystem.exception.account.InvalidAccountDigitException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.service.AccountService;
import com.lucas.bankingsystem.service.security.CustomUserDetailsService;
import com.lucas.bankingsystem.service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AccountControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;


    @Nested
    @DisplayName("GET /api/v1/accounts")
    class FindAll {

        @Test
        @DisplayName("Should return all accounts successfully")
        void shouldReturnAllAccountsSuccessfully() throws Exception {
            AccountResponseDTO account1 = mockAccount(
                    "99999",
                    "5",
                    AccountType.CHECKING
            );

            AccountResponseDTO account2 = mockAccount(
                    "99998",
                    "6",
                    AccountType.SAVINGS
            );

            when(accountService.findAll()).thenReturn(List.of(account1, account2));

            MvcResult result = mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].accountNumber").value(account1.accountNumber()))
                    .andExpect(jsonPath("$[0].accountDigit").value(account1.accountDigit()))
                    .andExpect(jsonPath("$[0].clientName").value(account1.clientName()))
                    .andExpect(jsonPath("$[0].type").value(account1.type().name()))
                    .andExpect(jsonPath("$[0].status").value(account1.status().name()))
                    .andExpect(jsonPath("$[1].accountNumber").value(account2.accountNumber()))
                    .andExpect(jsonPath("$[1].accountDigit").value(account2.accountDigit()))
                    .andExpect(jsonPath("$[1].clientName").value(account2.clientName()))
                    .andExpect(jsonPath("$[1].type").value(account2.type().name()))
                    .andExpect(jsonPath("$[1].status").value(account2.status().name()))
                    .andReturn();

            JsonNode response = objectMapper.readTree(
                    result.getResponse().getContentAsString()
            );

            assertBalanceEquals(
                    account1.balance(),
                    response.get(0).get("balance")
            );

            assertBalanceEquals(
                    account2.balance(),
                    response.get(1).get("balance")
            );

            verify(accountService).findAll();
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return empty list when there are no accounts")
        void shouldReturnEmptyListWhenThereAreNoAccounts() throws Exception {
            when(accountService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(accountService).findAll();
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(accountService.findAll()).thenThrow(new RuntimeException("Unexpected exception"));

            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(accountService).findAll();
            verifyNoMoreInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/{accountNumber}")
    class FindByAccountNumber {

        @Test
        @DisplayName("Should return the account successfully")
        void shouldReturnTheAccountSuccessfully() throws Exception {
            AccountResponseDTO account = mockAccount(
                    "99999",
                    "5",
                    AccountType.CHECKING
            );

            when(accountService.findByAccountNumber(account.accountNumber(), account.accountDigit()))
                    .thenReturn(account);

            MvcResult result = mockMvc.perform(get("/api/v1/accounts/{accountNumber}", account.accountNumber())
                            .param("digit", account.accountDigit()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.accountNumber").value(account.accountNumber()))
                    .andExpect(jsonPath("$.accountDigit").value(account.accountDigit()))
                    .andExpect(jsonPath("$.clientName").value(account.clientName()))
                    .andExpect(jsonPath("$.type").value(account.type().name()))
                    .andExpect(jsonPath("$.status").value(account.status().name()))
                    .andReturn();

            JsonNode response = objectMapper.readTree(
                    result.getResponse().getContentAsString()
            );

            assertBalanceEquals(
                    account.balance(),
                    response.get("balance")
            );

            verify(accountService).findByAccountNumber(account.accountNumber(), account.accountDigit());
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            when(accountService.findByAccountNumber("99999", "3"))
                    .thenThrow(new InvalidAccountDigitException("Dígito da conta inválido."));

            mockMvc.perform(get("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Dígito da conta inválido."));

            verify(accountService).findByAccountNumber("99999", "3");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            when(accountService.findByAccountNumber("99999", "5"))
                    .thenThrow(new AccountNotFoundException("Conta não encontrada."));

            mockMvc.perform(get("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "5"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Conta não encontrada."));

            verify(accountService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(accountService.findByAccountNumber("99999", "5"))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            mockMvc.perform(get("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "5"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(accountService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/accounts")
    class Create {

        @Test
        @DisplayName("Should create an account successfully")
        void shouldCreateAnAccountSuccessfully() throws Exception {
            AccountRequestDTO request = new AccountRequestDTO(
                    1L,
                    AccountType.CHECKING
            );

            AccountResponseDTO expectedResponse = mockAccount(
                    "12345",
                    "6",
                    AccountType.CHECKING
            );

            when(accountService.create(request)).thenReturn(expectedResponse);

            MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.accountNumber").value(expectedResponse.accountNumber()))
                    .andExpect(jsonPath("$.accountDigit").value(expectedResponse.accountDigit()))
                    .andExpect(jsonPath("$.clientName").value(expectedResponse.clientName()))
                    .andExpect(jsonPath("$.type").value(expectedResponse.type().name()))
                    .andExpect(jsonPath("$.status").value(expectedResponse.status().name()))
                    .andReturn();

            JsonNode response = objectMapper.readTree(
                    result.getResponse().getContentAsString()
            );

            assertBalanceEquals(
                    expectedResponse.balance(),
                    response.get("balance")
            );

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when client ID is null")
        void shouldReturnBadRequestWhenClientIdIsNull() throws Exception {
            assertValidationError(
                    new AccountRequestDTO(null, AccountType.CHECKING),
                    "clientId",
                    "O ID do titular é obrigatório."
            );
        }

        @Test
        @DisplayName("Should return 400 when client ID is not positive")
        void shouldReturnBadRequestWhenClientIdIsNotPositive() throws Exception {
            assertValidationError(
                    new AccountRequestDTO(0L, AccountType.CHECKING),
                    "clientId",
                    "O ID do titular deve ser maior que zero."
            );
        }

        @Test
        @DisplayName("Should return 400 when account type is null")
        void shouldReturnBadRequestWhenTypeIsNull() throws Exception {
            assertValidationError(
                    new AccountRequestDTO(1L, null),
                    "type",
                    "O tipo de conta é obrigatório."
            );
        }

        @Test
        @DisplayName("Should return 400 when account type is not a valid enum value")
        void shouldReturnBadRequestWhenTypeIsInvalidEnumValue() throws Exception {
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"clientId": 1, "type": "INVALID"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Dados da requisição inválidos."));

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when request body is malformed")
        void shouldReturnBadRequestWhenRequestBodyIsMalformed() throws Exception {
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{clientId: "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Dados da requisição inválidos."));

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Dados da requisição inválidos."));

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            AccountRequestDTO request = new AccountRequestDTO(99L, AccountType.CHECKING);

            when(accountService.create(request))
                    .thenThrow(new ClientNotFoundException("Cliente não encontrado."));

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Cliente não encontrado."));

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 409 when client already has an account of the same type")
        void shouldReturnConflictWhenAccountAlreadyExists() throws Exception {
            AccountRequestDTO request = new AccountRequestDTO(1L, AccountType.CHECKING);

            when(accountService.create(request))
                    .thenThrow(new AccountAlreadyExistsException("O cliente já possui uma conta corrente."));

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("O cliente já possui uma conta corrente."));

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            AccountRequestDTO request = new AccountRequestDTO(1L, AccountType.CHECKING);

            when(accountService.create(request))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }

        private void assertValidationError(
                AccountRequestDTO request,
                String field,
                String message
        ) throws Exception {
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Erro de validação."))
                    .andExpect(jsonPath("$.errors." + field).value(message));

            verifyNoInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/accounts/{accountNumber}")
    class Cancel {

        @Test
        @DisplayName("Should cancel the account successfully")
        void shouldCancelTheAccountSuccessfully() throws Exception {
            mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "5"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            doThrow(new InvalidAccountDigitException("Dígito da conta inválido."))
                    .when(accountService).cancel("99999", "3");

            mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Dígito da conta inválido."));

            verify(accountService).cancel("99999", "3");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            doThrow(new AccountNotFoundException("Conta não encontrada."))
                    .when(accountService).cancel("99999", "5");

            mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "5"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Conta não encontrada."));

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 409 when account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            doThrow(new AccountIsNotActiveException("Não é possível cancelar uma conta que não está ativa."))
                    .when(accountService).cancel("99999", "5");

            mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "5"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("Não é possível cancelar uma conta que não está ativa."));

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 409 when account has balance")
        void shouldReturnConflictWhenAccountHasBalance() throws Exception {
            doThrow(new AccountHasBalanceException("Não é possível cancelar uma conta com saldo."))
                    .when(accountService).cancel("99999", "5");

            mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "5"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("Não é possível cancelar uma conta com saldo."));

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            doThrow(new RuntimeException("Unexpected exception"))
                    .when(accountService).cancel("99999", "5");

            mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", "99999")
                            .param("digit", "5"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }
    }

    private static void assertBalanceEquals(
            BigDecimal expected,
            JsonNode actual
    ) {
        assertEquals(0, expected.compareTo(actual.decimalValue()));
    }

    private static AccountResponseDTO mockAccount(
            String accountNumber,
            String accountDigit,
            AccountType type
    ) {
        return new AccountResponseDTO(
                accountNumber,
                accountDigit,
                "Lucas Alves",
                new BigDecimal("500.40"),
                type,
                AccountStatus.ACTIVE
        );
    }
}