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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

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

    private static final String PROVIDER = "com.lucas.bankingsystem.controller.AccountControllerTests#";
    private static final String INTERNAL_ERROR_MESSAGE = "Ocorreu um erro interno no servidor.";
    private static final String INVALID_BODY_MESSAGE = "Dados da requisição inválidos.";
    private static final String VALIDATION_MESSAGE = "Erro de validação.";
    private static final String MISSING_PARAM_MESSAGE = "Parâmetro de requisição obrigatório ausente.";
    private static final String INVALID_ACCOUNT_NUMBER_MESSAGE = "O número da conta deve conter entre 5 e 20 dígitos.";
    private static final String INVALID_ACCOUNT_DIGIT_MESSAGE = "O dígito da conta deve conter 1 dígito.";

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

        private static final String URL = "/api/v1/accounts";

        @Test
        @DisplayName("Should return all accounts successfully")
        void shouldReturnAllAccountsSuccessfully() throws Exception {
            AccountResponseDTO account1 = mockAccount("99999", "5", AccountType.CHECKING);
            AccountResponseDTO account2 = mockAccount("99998", "6", AccountType.SAVINGS);

            when(accountService.findAll()).thenReturn(List.of(account1, account2));

            MvcResult result = mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andReturn();

            AccountResponseDTO[] response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    AccountResponseDTO[].class
            );

            assertAccountEquals(account1, response[0]);
            assertAccountEquals(account2, response[1]);

            verify(accountService).findAll();
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return empty list when there are no accounts")
        void shouldReturnEmptyListWhenThereAreNoAccounts() throws Exception {
            when(accountService.findAll()).thenReturn(List.of());

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(accountService).findAll();
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(accountService.findAll())
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performErrorResponse(mockMvc.perform(get(URL)), 500, INTERNAL_ERROR_MESSAGE);

            verify(accountService).findAll();
            verifyNoMoreInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/{accountNumber}")
    class FindByAccountNumber {

        private static final String URL = "/api/v1/accounts/{accountNumber}";

        @Test
        @DisplayName("Should return the account successfully")
        void shouldReturnTheAccountSuccessfully() throws Exception {
            AccountResponseDTO expected = mockAccount("99999", "5", AccountType.CHECKING);

            when(accountService.findByAccountNumber("99999", "5")).thenReturn(expected);

            MvcResult result = mockMvc.perform(get(URL, "99999")
                            .param("digit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertAccountEquals(expected, readAccount(result));

            verify(accountService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            when(accountService.findByAccountNumber("99999", "3"))
                    .thenThrow(new InvalidAccountDigitException("Dígito da conta inválido."));

            performGet("3", 400, "Dígito da conta inválido.");

            verify(accountService).findByAccountNumber("99999", "3");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            when(accountService.findByAccountNumber("99999", "5"))
                    .thenThrow(new AccountNotFoundException("Conta não encontrada."));

            performGet("5", 404, "Conta não encontrada.");

            verify(accountService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(accountService.findByAccountNumber("99999", "5"))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performGet("5", 500, INTERNAL_ERROR_MESSAGE);

            verify(accountService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when digit is missing")
        void shouldReturnBadRequestWhenDigitIsMissing() throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(URL, "99999")),
                    400,
                    MISSING_PARAM_MESSAGE
            );

            verifyNoInteractions(accountService);
        }

        private void performGet(String digit, int status, String message) throws Exception {
            ResultActions result = mockMvc.perform(
                    get(URL, "99999")
                            .param("digit", digit)
            );

            performErrorResponse(result, status, message);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAccountNumbers")
        @DisplayName("Should return 400 when account number is invalid")
        void shouldReturnBadRequestWhenAccountNumberIsInvalid(
                String scenario,
                String accountNumber
        ) throws Exception {
            mockMvc.perform(get(URL, accountNumber)
                            .param("digit", "5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(jsonPath("$.errors.accountNumber")
                            .value(INVALID_ACCOUNT_NUMBER_MESSAGE));

            verifyNoInteractions(accountService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAccountDigits")
        @DisplayName("Should return 400 when account digit has invalid format")
        void shouldReturnBadRequestWhenAccountDigitHasInvalidFormat(
                String scenario,
                String digit
        ) throws Exception {
            mockMvc.perform(get(URL, "99999")
                            .param("digit", digit))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(jsonPath("$.errors.digit")
                            .value(INVALID_ACCOUNT_DIGIT_MESSAGE));

            verifyNoInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/accounts")
    class Create {

        private static final String URL = "/api/v1/accounts";

        @Test
        @DisplayName("Should create an account successfully")
        void shouldCreateAnAccountSuccessfully() throws Exception {
            AccountRequestDTO request = accountRequest();
            AccountResponseDTO expected = mockAccount("12345", "6", AccountType.CHECKING);

            when(accountService.create(request)).thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertAccountEquals(expected, readAccount(result));

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAccountRequests")
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid(
                String scenario,
                AccountRequestDTO request,
                String field,
                String message
        ) throws Exception {
            performValidationError(request, field, message);

            verifyNoInteractions(accountService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "unreadableRequestBodies")
        @DisplayName("Should return 400 when request body cannot be read")
        void shouldReturnBadRequestWhenRequestBodyCannotBeRead(
                String scenario,
                String body
        ) throws Exception {
            ResultActions result = mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));

            performErrorResponse(result, 400, INVALID_BODY_MESSAGE);

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody();

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            AccountRequestDTO request = accountRequest();

            when(accountService.create(request))
                    .thenThrow(new ClientNotFoundException("Cliente não encontrado."));

            performPost(request, 404, "Cliente não encontrado.");

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 409 when client already has an account of the same type")
        void shouldReturnConflictWhenAccountAlreadyExists() throws Exception {
            AccountRequestDTO request = accountRequest();

            when(accountService.create(request))
                    .thenThrow(new AccountAlreadyExistsException("O cliente já possui uma conta corrente."));

            performPost(request, 409, "O cliente já possui uma conta corrente.");

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            AccountRequestDTO request = accountRequest();

            when(accountService.create(request))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performPost(request, 500, INTERNAL_ERROR_MESSAGE);

            verify(accountService).create(request);
            verifyNoMoreInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/accounts/{accountNumber}")
    class Cancel {

        private static final String URL = "/api/v1/accounts/{accountNumber}";

        @Test
        @DisplayName("Should cancel the account successfully")
        void shouldCancelTheAccountSuccessfully() throws Exception {
            mockMvc.perform(patch(URL, "99999")
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

            performCancel("3", 400, "Dígito da conta inválido.");

            verify(accountService).cancel("99999", "3");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            doThrow(new AccountNotFoundException("Conta não encontrada."))
                    .when(accountService).cancel("99999", "5");

            performCancel("5", 404, "Conta não encontrada.");

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 409 when account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            doThrow(new AccountIsNotActiveException("Não é possível cancelar uma conta que não está ativa."))
                    .when(accountService).cancel("99999", "5");

            performCancel("5", 409, "Não é possível cancelar uma conta que não está ativa.");

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 409 when account has balance")
        void shouldReturnConflictWhenAccountHasBalance() throws Exception {
            doThrow(new AccountHasBalanceException("Não é possível cancelar uma conta com saldo."))
                    .when(accountService).cancel("99999", "5");

            performCancel("5", 409, "Não é possível cancelar uma conta com saldo.");

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            doThrow(new RuntimeException("Unexpected exception"))
                    .when(accountService).cancel("99999", "5");

            performCancel("5", 500, INTERNAL_ERROR_MESSAGE);

            verify(accountService).cancel("99999", "5");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when digit is missing")
        void shouldReturnBadRequestWhenDigitIsMissing() throws Exception {
            performErrorResponse(
                    mockMvc.perform(patch(URL, "99999")),
                    400,
                    MISSING_PARAM_MESSAGE
            );

            verifyNoInteractions(accountService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAccountNumbers")
        @DisplayName("Should return 400 when account number is invalid")
        void shouldReturnBadRequestWhenAccountNumberIsInvalid(
                String scenario,
                String accountNumber
        ) throws Exception {
            mockMvc.perform(patch(URL, accountNumber)
                            .param("digit", "5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(jsonPath("$.errors.accountNumber")
                            .value(INVALID_ACCOUNT_NUMBER_MESSAGE));

            verifyNoInteractions(accountService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAccountDigits")
        @DisplayName("Should return 400 when account digit has invalid format")
        void shouldReturnBadRequestWhenAccountDigitHasInvalidFormat(
                String scenario,
                String digit
        ) throws Exception {
            mockMvc.perform(patch(URL, "99999")
                            .param("digit", digit))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(jsonPath("$.errors.digit")
                            .value(INVALID_ACCOUNT_DIGIT_MESSAGE));

            verifyNoInteractions(accountService);
        }

        private void performCancel(String digit, int status, String message) throws Exception {
            ResultActions result = mockMvc.perform(
                    patch(URL, "99999")
                            .param("digit", digit)
            );

            performErrorResponse(result, status, message);
        }
    }

    // Cada cenário tem UMA violação: o @NotNull e o @Positive de clientId não falham juntos
    // (null só viola @NotNull; 0 e -1 só violam @Positive).
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> invalidAccountRequests() {
        return Stream.of(
                Arguments.of("client ID is null",
                        new AccountRequestDTO(null, AccountType.CHECKING),
                        "clientId", "O ID do titular é obrigatório."),
                Arguments.of("client ID is zero",
                        new AccountRequestDTO(0L, AccountType.CHECKING),
                        "clientId", "O ID do titular deve ser maior que zero."),
                Arguments.of("client ID is negative",
                        new AccountRequestDTO(-1L, AccountType.CHECKING),
                        "clientId", "O ID do titular deve ser maior que zero."),
                Arguments.of("account type is null",
                        new AccountRequestDTO(1L, null),
                        "type", "O tipo de conta é obrigatório.")
        );
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> invalidAccountNumbers() {
        return Stream.of(
                Arguments.of("account number is too short", "1234"),
                Arguments.of("account number exceeds maximum length", "123456789012345678901"),
                Arguments.of("account number contains letters", "1234A"),
                Arguments.of("account number contains special characters", "123-5")
        );
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> invalidAccountDigits() {
        return Stream.of(
                Arguments.of("account digit is empty", ""),
                Arguments.of("account digit contains multiple digits", "12"),
                Arguments.of("account digit contains letters", "A"),
                Arguments.of("account digit contains special characters", "-")
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> unreadableRequestBodies() {
        return Stream.of(
                Arguments.of("request body is malformed",
                        "{clientId: "),
                Arguments.of("account type is not a valid enum value",
                        "{\"clientId\": 1, \"type\": \"INVALID\"}"),
                Arguments.of("client ID is not a number",
                        "{\"clientId\": \"abc\", \"type\": \"CHECKING\"}")
        );
    }

    private void performPost(Object request, int status, String message) throws Exception {
        ResultActions result = mockMvc.perform(post(Create.URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        performErrorResponse(result, status, message);
    }

    private void performErrorResponse(
            ResultActions result,
            int status,
            String message
    ) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message));
    }

    private void performValidationError(
            Object request,
            String field,
            String message
    ) throws Exception {
        mockMvc.perform(post(Create.URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                .andExpect(jsonPath("$.errors." + field).value(message));
    }

    private void performMissingBody() throws Exception {
        mockMvc.perform(post(Create.URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(INVALID_BODY_MESSAGE));
    }

    private AccountResponseDTO readAccount(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AccountResponseDTO.class
        );
    }

    private static void assertAccountEquals(
            AccountResponseDTO expected,
            AccountResponseDTO actual
    ) {
        assertEquals(expected.accountNumber(), actual.accountNumber());
        assertEquals(expected.accountDigit(), actual.accountDigit());
        assertEquals(expected.clientName(), actual.clientName());
        assertEquals(0, expected.balance().compareTo(actual.balance()));
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.status(), actual.status());
    }

    private static AccountRequestDTO accountRequest() {
        return new AccountRequestDTO(1L, AccountType.CHECKING);
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