package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lucas.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lucas.bankingsystem.dto.response.TransactionResponseDTO;
import com.lucas.bankingsystem.entity.enums.TransactionType;
import com.lucas.bankingsystem.exception.account.*;
import com.lucas.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lucas.bankingsystem.exception.transaction.TransactionNotFoundException;
import com.lucas.bankingsystem.exception.transaction.YieldAlreadyAppliedException;
import com.lucas.bankingsystem.exception.transaction.YieldNotAvailableException;
import com.lucas.bankingsystem.service.transaction.TransactionService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TransactionControllerTests {

    private static final String PROVIDER =
            "com.lucas.bankingsystem.controller.TransactionControllerTests#";

    private static final String INTERNAL_ERROR_MESSAGE =
            "Ocorreu um erro interno no servidor.";

    private static final String INVALID_BODY_MESSAGE =
            "Dados da requisição inválidos.";

    private static final String VALIDATION_MESSAGE =
            "Erro de validação.";

    private static final String MISSING_PARAM_MESSAGE =
            "Parâmetro de requisição obrigatório ausente.";

    private static final String INVALID_REQUEST_PARAM_MESSAGE =
            "Parâmetro de requisição inválido.";

    private static final String INVALID_ACCOUNT_NUMBER_MESSAGE =
            "O número da conta deve conter entre 5 e 20 dígitos.";

    private static final String INVALID_ACCOUNT_DIGIT_MESSAGE =
            "O dígito da conta deve conter 1 dígito.";

    private static final String INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE =
            "Dígito da conta inválido.";

    private static final String ACCOUNT_NOT_FOUND_MESSAGE =
            "Conta não encontrada.";

    private static final String ACCOUNT_NOT_ACTIVE_MESSAGE =
            "A conta informada não está ativa.";

    private static final String INSUFFICIENT_BALANCE_MESSAGE =
            "O valor informado é maior do que o saldo.";

    private static final String ACCOUNTS_ARE_SAME_MESSAGE =
            "A conta de origem não pode ser igual à conta de destino.";

    private static final String ACCOUNT_NOT_SAVINGS_MESSAGE =
            "A conta informada não é poupança.";

    private static final String YIELD_NOT_AVAILABLE_MESSAGE =
            "Não há rendimento disponível para esta conta.";

    private static final String YIELD_ALREADY_APPLIED_MESSAGE =
            "O rendimento já foi aplicado para a conta hoje.";

    private static final String TRANSACTION_NOT_FOUND_MESSAGE =
            "Transação não encontrada.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Nested
    @DisplayName("POST /api/v1/transactions/deposit")
    class Deposit {

        private static final String URL = "/api/v1/transactions/deposit";

        @Test
        @DisplayName("Should deposit successfully")
        void shouldDepositSuccessfully() throws Exception {
            AccountOperationRequestDTO request = operationRequest();
            TransactionResponseDTO expected =
                    mockTransaction(TransactionType.DEPOSIT);

            when(transactionService.deposit(request)).thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionService).deposit(request);
            verifyNoMoreInteractions(transactionService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidOperationRequests")
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid(
                String scenario,
                AccountOperationRequestDTO request,
                String field,
                String message
        ) throws Exception {
            performValidationError(URL, request, field, message);

            verifyNoInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(URL);

            verifyNoInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.deposit(request))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE
                            )
                    );

            performPost(URL, request, 400, INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE);

            verify(transactionService).deposit(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.deposit(request))
                    .thenThrow(
                            new AccountNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE)
                    );

            performPost(URL, request, 404, ACCOUNT_NOT_FOUND_MESSAGE);

            verify(transactionService).deposit(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.deposit(request))
                    .thenThrow(
                            new AccountIsNotActiveException(ACCOUNT_NOT_ACTIVE_MESSAGE)
                    );

            performPost(URL, request, 409, ACCOUNT_NOT_ACTIVE_MESSAGE);

            verify(transactionService).deposit(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.deposit(request))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performPost(URL, request, 500, INTERNAL_ERROR_MESSAGE);

            verify(transactionService).deposit(request);
            verifyNoMoreInteractions(transactionService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/transactions/withdraw")
    class Withdraw {

        private static final String URL = "/api/v1/transactions/withdraw";

        @Test
        @DisplayName("Should withdraw successfully")
        void shouldWithdrawSuccessfully() throws Exception {
            AccountOperationRequestDTO request = operationRequest();
            TransactionResponseDTO expected =
                    mockTransaction(TransactionType.WITHDRAWAL);

            when(transactionService.withdraw(request)).thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionService).withdraw(request);
            verifyNoMoreInteractions(transactionService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidOperationRequests")
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid(
                String scenario,
                AccountOperationRequestDTO request,
                String field,
                String message
        ) throws Exception {
            performValidationError(URL, request, field, message);

            verifyNoInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(URL);

            verifyNoInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.withdraw(request))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE
                            )
                    );

            performPost(URL, request, 400, INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE);

            verify(transactionService).withdraw(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.withdraw(request))
                    .thenThrow(
                            new AccountNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE)
                    );

            performPost(URL, request, 404, ACCOUNT_NOT_FOUND_MESSAGE);

            verify(transactionService).withdraw(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when balance is insufficient")
        void shouldReturnConflictWhenBalanceIsInsufficient() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.withdraw(request))
                    .thenThrow(
                            new InsufficientBalanceException(
                                    INSUFFICIENT_BALANCE_MESSAGE
                            )
                    );

            performPost(URL, request, 409, INSUFFICIENT_BALANCE_MESSAGE);

            verify(transactionService).withdraw(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.withdraw(request))
                    .thenThrow(
                            new AccountIsNotActiveException(ACCOUNT_NOT_ACTIVE_MESSAGE)
                    );

            performPost(URL, request, 409, ACCOUNT_NOT_ACTIVE_MESSAGE);

            verify(transactionService).withdraw(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionService.withdraw(request))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performPost(URL, request, 500, INTERNAL_ERROR_MESSAGE);

            verify(transactionService).withdraw(request);
            verifyNoMoreInteractions(transactionService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/transactions/transfer")
    class Transfer {

        private static final String URL = "/api/v1/transactions/transfer";

        @Test
        @DisplayName("Should transfer successfully")
        void shouldTransferSuccessfully() throws Exception {
            TransferRequestDTO request = transferRequest();
            TransactionResponseDTO expected =
                    mockTransaction(TransactionType.TRANSFER_SENT);

            when(transactionService.transfer(request)).thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionService).transfer(request);
            verifyNoMoreInteractions(transactionService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidTransferRequests")
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid(
                String scenario,
                TransferRequestDTO request,
                String field,
                String message
        ) throws Exception {
            performValidationError(URL, request, field, message);

            verifyNoInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(URL);

            verifyNoInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionService.transfer(request))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE
                            )
                    );

            performPost(URL, request, 400, INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE);

            verify(transactionService).transfer(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 404 when an account does not exist")
        void shouldReturnNotFoundWhenAnAccountDoesNotExist() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionService.transfer(request))
                    .thenThrow(
                            new AccountNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE)
                    );

            performPost(URL, request, 404, ACCOUNT_NOT_FOUND_MESSAGE);

            verify(transactionService).transfer(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when accounts are the same")
        void shouldReturnConflictWhenAccountsAreSame() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionService.transfer(request))
                    .thenThrow(
                            new AccountsAreSameException(ACCOUNTS_ARE_SAME_MESSAGE)
                    );

            performPost(URL, request, 409, ACCOUNTS_ARE_SAME_MESSAGE);

            verify(transactionService).transfer(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when balance is insufficient")
        void shouldReturnConflictWhenBalanceIsInsufficient() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionService.transfer(request))
                    .thenThrow(
                            new InsufficientBalanceException(
                                    INSUFFICIENT_BALANCE_MESSAGE
                            )
                    );

            performPost(URL, request, 409, INSUFFICIENT_BALANCE_MESSAGE);

            verify(transactionService).transfer(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when an account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionService.transfer(request))
                    .thenThrow(
                            new AccountIsNotActiveException(ACCOUNT_NOT_ACTIVE_MESSAGE)
                    );

            performPost(URL, request, 409, ACCOUNT_NOT_ACTIVE_MESSAGE);

            verify(transactionService).transfer(request);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionService.transfer(request))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performPost(URL, request, 500, INTERNAL_ERROR_MESSAGE);

            verify(transactionService).transfer(request);
            verifyNoMoreInteractions(transactionService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/transactions/yield/{accountNumber}")
    class ApplyYield {

        private static final String URL = "/api/v1/transactions/yield/{accountNumber}";

        @Test
        @DisplayName("Should apply yield successfully")
        void shouldApplyYieldSuccessfully() throws Exception {
            TransactionResponseDTO expected =
                    mockTransaction(TransactionType.YIELD);

            when(transactionService.applyYield("99999", "5"))
                    .thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL, "99999")
                            .param("digit", "5"))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionService).applyYield("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            when(transactionService.applyYield("99999", "3"))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE
                            )
                    );

            performYield("3", 400, INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE);

            verify(transactionService).applyYield("99999", "3");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            when(transactionService.applyYield("99999", "5"))
                    .thenThrow(
                            new AccountNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE)
                    );

            performYield("5", 404, ACCOUNT_NOT_FOUND_MESSAGE);

            verify(transactionService).applyYield("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            when(transactionService.applyYield("99999", "5"))
                    .thenThrow(
                            new AccountIsNotActiveException(ACCOUNT_NOT_ACTIVE_MESSAGE)
                    );

            performYield("5", 409, ACCOUNT_NOT_ACTIVE_MESSAGE);

            verify(transactionService).applyYield("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when account is not a savings account")
        void shouldReturnConflictWhenAccountIsNotSavings() throws Exception {
            when(transactionService.applyYield("99999", "5"))
                    .thenThrow(
                            new AccountIsNotSavingsException(ACCOUNT_NOT_SAVINGS_MESSAGE)
                    );

            performYield("5", 409, ACCOUNT_NOT_SAVINGS_MESSAGE);

            verify(transactionService).applyYield("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when yield is not available")
        void shouldReturnConflictWhenYieldIsNotAvailable() throws Exception {
            when(transactionService.applyYield("99999", "5"))
                    .thenThrow(
                            new YieldNotAvailableException(
                                    YIELD_NOT_AVAILABLE_MESSAGE
                            )
                    );

            performYield("5", 409, YIELD_NOT_AVAILABLE_MESSAGE);

            verify(transactionService).applyYield("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 409 when yield was already applied today")
        void shouldReturnConflictWhenYieldWasAlreadyApplied() throws Exception {
            when(transactionService.applyYield("99999", "5"))
                    .thenThrow(
                            new YieldAlreadyAppliedException(
                                    YIELD_ALREADY_APPLIED_MESSAGE
                            )
                    );

            performYield("5", 409, YIELD_ALREADY_APPLIED_MESSAGE);

            verify(transactionService).applyYield("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(transactionService.applyYield("99999", "5"))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performYield("5", 500, INTERNAL_ERROR_MESSAGE);

            verify(transactionService).applyYield("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when digit is missing")
        void shouldReturnBadRequestWhenDigitIsMissing() throws Exception {
            mockMvc.perform(post(URL, "99999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(MISSING_PARAM_MESSAGE));

            verifyNoInteractions(transactionService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAccountNumbers")
        @DisplayName("Should return 400 when account number is invalid")
        void shouldReturnBadRequestWhenAccountNumberIsInvalid(
                String scenario,
                String accountNumber
        ) throws Exception {
            mockMvc.perform(post(URL, accountNumber)
                            .param("digit", "5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(
                            jsonPath("$.errors.accountNumber")
                                    .value(INVALID_ACCOUNT_NUMBER_MESSAGE)
                    );

            verifyNoInteractions(transactionService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAccountDigits")
        @DisplayName("Should return 400 when account digit has invalid format")
        void shouldReturnBadRequestWhenAccountDigitHasInvalidFormat(
                String scenario,
                String digit
        ) throws Exception {
            mockMvc.perform(post(URL, "99999")
                            .param("digit", digit))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(
                            jsonPath("$.errors.digit")
                                    .value(INVALID_ACCOUNT_DIGIT_MESSAGE)
                    );

            verifyNoInteractions(transactionService);
        }

        private void performYield(
                String digit,
                int status,
                String message
        ) throws Exception {
            ResultActions result =
                    mockMvc.perform(post(URL, "99999")
                            .param("digit", digit));

            performErrorResponse(result, status, message);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transactions/accounts/{accountNumber}")
    class FindByAccountNumber {

        private static final String URL =
                "/api/v1/transactions/accounts/{accountNumber}";

        @Test
        @DisplayName("Should return the account transactions successfully")
        void shouldReturnTheAccountTransactionsSuccessfully() throws Exception {
            TransactionResponseDTO transaction1 =
                    mockTransaction(TransactionType.DEPOSIT);

            TransactionResponseDTO transaction2 =
                    mockTransaction(TransactionType.WITHDRAWAL);

            when(transactionService.findByAccountNumber("99999", "5"))
                    .thenReturn(List.of(transaction1, transaction2));

            MvcResult result = mockMvc.perform(get(URL, "99999")
                            .param("digit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andReturn();

            TransactionResponseDTO[] response =
                    objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            TransactionResponseDTO[].class
                    );

            assertTransactionEquals(transaction1, response[0]);
            assertTransactionEquals(transaction2, response[1]);

            verify(transactionService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return empty list when the account has no transactions")
        void shouldReturnEmptyListWhenAccountHasNoTransactions() throws Exception {
            when(transactionService.findByAccountNumber("99999", "5"))
                    .thenReturn(List.of());

            mockMvc.perform(get(URL, "99999")
                            .param("digit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(transactionService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            when(transactionService.findByAccountNumber("99999", "3"))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE
                            )
                    );

            performGet("3", 400, INVALID_ACCOUNT_DIGIT_BUSINESS_MESSAGE);

            verify(transactionService).findByAccountNumber("99999", "3");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            when(transactionService.findByAccountNumber("99999", "5"))
                    .thenThrow(
                            new AccountNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE)
                    );

            performGet("5", 404, ACCOUNT_NOT_FOUND_MESSAGE);

            verify(transactionService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(transactionService.findByAccountNumber("99999", "5"))
                    .thenThrow(new RuntimeException("Unexpected exception"));

            performGet("5", 500, INTERNAL_ERROR_MESSAGE);

            verify(transactionService).findByAccountNumber("99999", "5");
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when digit is missing")
        void shouldReturnBadRequestWhenDigitIsMissing() throws Exception {
            mockMvc.perform(get(URL, "99999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(MISSING_PARAM_MESSAGE));

            verifyNoInteractions(transactionService);
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
                    .andExpect(
                            jsonPath("$.errors.accountNumber")
                                    .value(INVALID_ACCOUNT_NUMBER_MESSAGE)
                    );

            verifyNoInteractions(transactionService);
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
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(
                            jsonPath("$.errors.digit")
                                    .value(INVALID_ACCOUNT_DIGIT_MESSAGE)
                    );

            verifyNoInteractions(transactionService);
        }

        private void performGet(
                String digit,
                int status,
                String message
        ) throws Exception {
            ResultActions result =
                    mockMvc.perform(get(URL, "99999")
                            .param("digit", digit));

            performErrorResponse(result, status, message);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transactions/code/{transactionCode}")
    class FindByTransactionCode {

        private static final String URL =
                "/api/v1/transactions/code/{transactionCode}";

        @Test
        @DisplayName("Should return transaction successfully")
        void shouldReturnTransactionSuccessfully() throws Exception {
            UUID transactionCode = UUID.randomUUID();

            TransactionResponseDTO expected = new TransactionResponseDTO(
                    transactionCode,
                    TransactionType.DEPOSIT,
                    new BigDecimal("100.00"),
                    LocalDateTime.of(2026, 1, 15, 10, 30, 45)
            );

            when(transactionService.findByTransactionCode(transactionCode))
                    .thenReturn(expected);

            MvcResult result =
                    performGetByTransactionCode(transactionCode.toString())
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                            .andExpect(
                                    jsonPath("$.transactionCode")
                                            .value(transactionCode.toString())
                            )
                            .andExpect(jsonPath("$.type").value("DEPOSIT"))
                            .andExpect(jsonPath("$.amount").value(100.00))
                            .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionService).findByTransactionCode(transactionCode);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 404 when transaction does not exist")
        void shouldReturnNotFoundWhenTransactionDoesNotExist() throws Exception {
            UUID transactionCode = UUID.randomUUID();

            when(transactionService.findByTransactionCode(transactionCode))
                    .thenThrow(
                            new TransactionNotFoundException(
                                    TRANSACTION_NOT_FOUND_MESSAGE
                            )
                    );

            performErrorResponse(
                    performGetByTransactionCode(transactionCode.toString()),
                    404,
                    TRANSACTION_NOT_FOUND_MESSAGE
            );

            verify(transactionService).findByTransactionCode(transactionCode);
            verifyNoMoreInteractions(transactionService);
        }

        @Test
        @DisplayName("Should return 400 when transaction UUID is invalid")
        void shouldReturnBadRequestWhenTransactionCodeIsInvalid() throws Exception {
            performErrorResponse(
                    performGetByTransactionCode("invalid-uuid"),
                    400,
                    INVALID_REQUEST_PARAM_MESSAGE
            );

            verifyNoInteractions(transactionService);
        }

        private ResultActions performGetByTransactionCode(
                String transactionCode
        ) throws Exception {
            return mockMvc.perform(get(URL, transactionCode));
        }
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> invalidOperationRequests() {
        BigDecimal amount = new BigDecimal("100.00");

        return Stream.of(
                Arguments.of(
                        "account number is null",
                        new AccountOperationRequestDTO(null, "5", amount),
                        "accountNumber",
                        "O número da conta é obrigatório."
                ),
                Arguments.of(
                        "account number is too short",
                        new AccountOperationRequestDTO("1234", "5", amount),
                        "accountNumber",
                        "O número da conta deve conter entre 5 e 20 dígitos."
                ),
                Arguments.of(
                        "digit is null",
                        new AccountOperationRequestDTO("99999", null, amount),
                        "digit",
                        "O dígito da conta é obrigatório."
                ),
                Arguments.of(
                        "digit does not have 1 digit",
                        new AccountOperationRequestDTO("99999", "12", amount),
                        "digit",
                        "O dígito da conta deve conter exatamente 1 dígito."
                ),
                Arguments.of(
                        "amount is null",
                        new AccountOperationRequestDTO("99999", "5", null),
                        "amount",
                        "O valor da operação é obrigatório."
                ),
                Arguments.of(
                        "amount is zero",
                        new AccountOperationRequestDTO(
                                "99999",
                                "5",
                                BigDecimal.ZERO
                        ),
                        "amount",
                        "O valor da operação deve ser maior que zero."
                ),
                Arguments.of(
                        "amount is negative",
                        new AccountOperationRequestDTO(
                                "99999",
                                "5",
                                new BigDecimal("-1.00")
                        ),
                        "amount",
                        "O valor da operação deve ser maior que zero."
                )
        );
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> invalidTransferRequests() {
        BigDecimal amount = new BigDecimal("100.00");

        return Stream.of(
                Arguments.of(
                        "origin account number is null",
                        new TransferRequestDTO(
                                null,
                                "5",
                                "88888",
                                "3",
                                amount
                        ),
                        "fromAccountNumber",
                        "O número da conta de origem é obrigatório."
                ),
                Arguments.of(
                        "origin account number is too short",
                        new TransferRequestDTO(
                                "1234",
                                "5",
                                "88888",
                                "3",
                                amount
                        ),
                        "fromAccountNumber",
                        "O número da conta de origem deve conter entre 5 e 20 dígitos."
                ),
                Arguments.of(
                        "origin digit is null",
                        new TransferRequestDTO(
                                "99999",
                                null,
                                "88888",
                                "3",
                                amount
                        ),
                        "fromAccountDigit",
                        "O dígito da conta de origem é obrigatório."
                ),
                Arguments.of(
                        "origin digit does not have 1 digit",
                        new TransferRequestDTO(
                                "99999",
                                "12",
                                "88888",
                                "3",
                                amount
                        ),
                        "fromAccountDigit",
                        "O dígito da conta de origem deve conter exatamente 1 dígito."
                ),
                Arguments.of(
                        "destination account number is null",
                        new TransferRequestDTO(
                                "99999",
                                "5",
                                null,
                                "3",
                                amount
                        ),
                        "toAccountNumber",
                        "O número da conta de destino é obrigatório."
                ),
                Arguments.of(
                        "destination account number is too short",
                        new TransferRequestDTO(
                                "99999",
                                "5",
                                "1234",
                                "3",
                                amount
                        ),
                        "toAccountNumber",
                        "O número da conta de destino deve conter entre 5 e 20 dígitos."
                ),
                Arguments.of(
                        "destination digit is null",
                        new TransferRequestDTO(
                                "99999",
                                "5",
                                "88888",
                                null,
                                amount
                        ),
                        "toAccountDigit",
                        "O dígito da conta de destino é obrigatório."
                ),
                Arguments.of(
                        "destination digit does not have 1 digit",
                        new TransferRequestDTO(
                                "99999",
                                "5",
                                "88888",
                                "12",
                                amount
                        ),
                        "toAccountDigit",
                        "O dígito da conta de destino deve conter exatamente 1 dígito."
                ),
                Arguments.of(
                        "amount is null",
                        new TransferRequestDTO(
                                "99999",
                                "5",
                                "88888",
                                "3",
                                null
                        ),
                        "amount",
                        "O valor da transferência é obrigatório."
                ),
                Arguments.of(
                        "amount is zero",
                        new TransferRequestDTO(
                                "99999",
                                "5",
                                "88888",
                                "3",
                                BigDecimal.ZERO
                        ),
                        "amount",
                        "O valor da transferência deve ser maior que zero."
                ),
                Arguments.of(
                        "amount is negative",
                        new TransferRequestDTO(
                                "99999",
                                "5",
                                "88888",
                                "3",
                                new BigDecimal("-1.00")
                        ),
                        "amount",
                        "O valor da transferência deve ser maior que zero."
                )
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

    private void performPost(
            String url,
            Object request,
            int status,
            String message
    ) throws Exception {
        ResultActions result = mockMvc.perform(
                post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        performErrorResponse(result, status, message);
    }

    private void performErrorResponse(
            ResultActions result,
            int status,
            String message
    ) throws Exception {
        result
                .andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message));
    }

    private void performValidationError(
            String url,
            Object request,
            String field,
            String message
    ) throws Exception {
        mockMvc.perform(
                        post(url)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                .andExpect(jsonPath("$.errors." + field).value(message));
    }

    private void performMissingBody(String url) throws Exception {
        mockMvc.perform(
                        post(url)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(INVALID_BODY_MESSAGE));
    }

    private TransactionResponseDTO readTransaction(MvcResult result)
            throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransactionResponseDTO.class
        );
    }

    private static void assertTransactionEquals(
            TransactionResponseDTO expected,
            TransactionResponseDTO actual
    ) {
        assertEquals(expected.transactionCode(), actual.transactionCode());
        assertEquals(expected.type(), actual.type());
        assertEquals(
                0,
                expected.amount().compareTo(actual.amount())
        );
        assertEquals(expected.createdAt(), actual.createdAt());
    }

    private static AccountOperationRequestDTO operationRequest() {
        return new AccountOperationRequestDTO(
                "99999",
                "5",
                new BigDecimal("100.00")
        );
    }

    private static TransferRequestDTO transferRequest() {
        return new TransferRequestDTO(
                "99999",
                "5",
                "88888",
                "3",
                new BigDecimal("100.00")
        );
    }

    private static TransactionResponseDTO mockTransaction(
            TransactionType type
    ) {
        return new TransactionResponseDTO(
                UUID.randomUUID(),
                type,
                new BigDecimal("100.00"),
                LocalDateTime.of(2026, 1, 15, 10, 30, 45)
        );
    }
}