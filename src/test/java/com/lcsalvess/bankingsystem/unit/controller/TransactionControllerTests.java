package com.lcsalvess.bankingsystem.unit.controller;

import com.lcsalvess.bankingsystem.config.pagination.PaginationConfig;
import com.lcsalvess.bankingsystem.controller.TransactionController;
import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountNotFoundException;
import com.lcsalvess.bankingsystem.exception.account.AccountsAreSameException;
import com.lcsalvess.bankingsystem.exception.account.InvalidAccountDigitException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.exception.transaction.TransactionNotFoundException;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyService;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcTestSecurityConfig.class,
        PaginationConfig.class
})
public class TransactionControllerTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.controller.TransactionControllerTests#";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionQueryService transactionQueryService;

    @MockitoBean
    private TransactionIdempotencyService transactionIdempotencyService;

    @Nested
    @DisplayName("POST /api/v1/transactions/deposits")
    class Deposit {

        private static final String URL = "/api/v1/transactions/deposits";

        @Test
        @DisplayName("Should deposit successfully")
        void shouldDepositSuccessfully() throws Exception {
            AccountOperationRequestDTO request = operationRequest();
            TransactionResponseDTO expected =
                    mockTransaction(TransactionType.DEPOSIT);

            when(transactionIdempotencyService.deposit(IDEMPOTENCY_KEY, request))
                    .thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL)
                            .header(IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionIdempotencyService).deposit(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
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

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(URL);

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.deposit(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    ApiErrorMessages.INVALID_ACCOUNT_DIGIT
                            )
                    );

            performPost(URL, request, 400, ApiErrorMessages.INVALID_ACCOUNT_DIGIT);

            verify(transactionIdempotencyService).deposit(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.deposit(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new AccountNotFoundException(
                                    ApiErrorMessages.ACCOUNT_NOT_FOUND
                            )
                    );

            performPost(URL, request, 404, ApiErrorMessages.ACCOUNT_NOT_FOUND);

            verify(transactionIdempotencyService).deposit(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 409 when account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.deposit(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new AccountIsNotActiveException(
                                    ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE
                            )
                    );

            performPost(URL, request, 409, ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE);

            verify(transactionIdempotencyService).deposit(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.deposit(IDEMPOTENCY_KEY, request))
                    .thenThrow(new RuntimeException());

            performPost(URL, request, 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verify(transactionIdempotencyService).deposit(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/transactions/withdrawals")
    class Withdraw {

        private static final String URL = "/api/v1/transactions/withdrawals";

        @Test
        @DisplayName("Should withdraw successfully")
        void shouldWithdrawSuccessfully() throws Exception {
            AccountOperationRequestDTO request = operationRequest();
            TransactionResponseDTO expected =
                    mockTransaction(TransactionType.WITHDRAWAL);

            when(transactionIdempotencyService.withdraw(IDEMPOTENCY_KEY, request))
                    .thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL)
                            .header(IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionIdempotencyService).withdraw(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
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

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(URL);

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.withdraw(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    ApiErrorMessages.INVALID_ACCOUNT_DIGIT
                            )
                    );

            performPost(URL, request, 400, ApiErrorMessages.INVALID_ACCOUNT_DIGIT);

            verify(transactionIdempotencyService).withdraw(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.withdraw(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new AccountNotFoundException(
                                    ApiErrorMessages.ACCOUNT_NOT_FOUND
                            )
                    );

            performPost(URL, request, 404, ApiErrorMessages.ACCOUNT_NOT_FOUND);

            verify(transactionIdempotencyService).withdraw(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 409 when balance is insufficient")
        void shouldReturnConflictWhenBalanceIsInsufficient() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.withdraw(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new InsufficientBalanceException(
                                    ApiErrorMessages.INSUFFICIENT_BALANCE
                            )
                    );

            performPost(URL, request, 409, ApiErrorMessages.INSUFFICIENT_BALANCE);

            verify(transactionIdempotencyService).withdraw(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 409 when account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.withdraw(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new AccountIsNotActiveException(
                                    ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE
                            )
                    );

            performPost(URL, request, 409, ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE);

            verify(transactionIdempotencyService).withdraw(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            AccountOperationRequestDTO request = operationRequest();

            when(transactionIdempotencyService.withdraw(IDEMPOTENCY_KEY, request))
                    .thenThrow(new RuntimeException());

            performPost(URL, request, 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verify(transactionIdempotencyService).withdraw(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/transactions/transfers")
    class Transfer {

        private static final String URL = "/api/v1/transactions/transfers";

        @Test
        @DisplayName("Should transfer successfully")
        void shouldTransferSuccessfully() throws Exception {
            TransferRequestDTO request = transferRequest();
            TransactionResponseDTO expected =
                    mockTransaction(TransactionType.TRANSFER_SENT);

            when(transactionIdempotencyService.transfer(IDEMPOTENCY_KEY, request))
                    .thenReturn(expected);

            MvcResult result = mockMvc.perform(post(URL)
                            .header(IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionIdempotencyService).transfer(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
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

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(URL);

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionIdempotencyService.transfer(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new InvalidAccountDigitException(
                                    ApiErrorMessages.INVALID_ACCOUNT_DIGIT
                            )
                    );

            performPost(URL, request, 400, ApiErrorMessages.INVALID_ACCOUNT_DIGIT);

            verify(transactionIdempotencyService).transfer(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 404 when an account does not exist")
        void shouldReturnNotFoundWhenAnAccountDoesNotExist() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionIdempotencyService.transfer(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new AccountNotFoundException(
                                    ApiErrorMessages.ACCOUNT_NOT_FOUND
                            )
                    );

            performPost(URL, request, 404, ApiErrorMessages.ACCOUNT_NOT_FOUND);

            verify(transactionIdempotencyService).transfer(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when accounts are the same")
        void shouldReturnConflictWhenAccountsAreSame() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionIdempotencyService.transfer(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new AccountsAreSameException(
                                    ApiErrorMessages.SAME_SOURCE_AND_DESTINATION_ACCOUNT
                            )
                    );

            performPost(
                    URL,
                    request,
                    400,
                    ApiErrorMessages.SAME_SOURCE_AND_DESTINATION_ACCOUNT
            );

            verify(transactionIdempotencyService).transfer(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 409 when balance is insufficient")
        void shouldReturnConflictWhenBalanceIsInsufficient() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionIdempotencyService.transfer(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new InsufficientBalanceException(
                                    ApiErrorMessages.INSUFFICIENT_BALANCE
                            )
                    );

            performPost(URL, request, 409, ApiErrorMessages.INSUFFICIENT_BALANCE);

            verify(transactionIdempotencyService).transfer(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 409 when an account is not active")
        void shouldReturnConflictWhenAccountIsNotActive() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionIdempotencyService.transfer(IDEMPOTENCY_KEY, request))
                    .thenThrow(
                            new AccountIsNotActiveException(
                                    ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE
                            )
                    );

            performPost(URL, request, 409, ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE);

            verify(transactionIdempotencyService).transfer(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            TransferRequestDTO request = transferRequest();

            when(transactionIdempotencyService.transfer(IDEMPOTENCY_KEY, request))
                    .thenThrow(new RuntimeException());

            performPost(URL, request, 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verify(transactionIdempotencyService).transfer(IDEMPOTENCY_KEY, request);
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transactions/{transactionCode}")
    class FindByTransactionCode {

        private static final String URL =
                "/api/v1/transactions/{transactionCode}";

        @Test
        @DisplayName("Should return transaction successfully")
        void shouldReturnTransactionSuccessfully() throws Exception {
            UUID transactionCode = UUID.randomUUID();

            TransactionResponseDTO expected = new TransactionResponseDTO(
                    transactionCode,
                    null,
                    TransactionType.DEPOSIT,
                    new BigDecimal("100.00"),
                    Instant.parse("2026-01-15T13:30:45Z"),
                    null,
                    new BigDecimal("100.00")
            );

            when(transactionQueryService.findByTransactionCode(transactionCode))
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
                            .andExpect(jsonPath("$.transferCode").doesNotExist())
                            .andReturn();

            assertTransactionEquals(expected, readTransaction(result));

            verify(transactionQueryService).findByTransactionCode(transactionCode);
            verifyNoMoreInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 404 when transaction does not exist")
        void shouldReturnNotFoundWhenTransactionDoesNotExist() throws Exception {
            UUID transactionCode = UUID.randomUUID();

            when(transactionQueryService.findByTransactionCode(transactionCode))
                    .thenThrow(
                            new TransactionNotFoundException(
                                    ApiErrorMessages.TRANSACTION_NOT_FOUND
                            )
                    );

            performErrorResponse(
                    performGetByTransactionCode(transactionCode.toString()),
                    404,
                    ApiErrorMessages.TRANSACTION_NOT_FOUND
            );

            verify(transactionQueryService).findByTransactionCode(transactionCode);
            verifyNoMoreInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when transaction UUID is invalid")
        void shouldReturnBadRequestWhenTransactionCodeIsInvalid() throws Exception {
            performErrorResponse(
                    performGetByTransactionCode("invalid-uuid"),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(transactionQueryService);
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
                        ValidationMessages.REQUIRED_ACCOUNT_NUMBER
                ),
                Arguments.of(
                        "account number is too short",
                        new AccountOperationRequestDTO("1234", "5", amount),
                        "accountNumber",
                        ValidationMessages.INVALID_ACCOUNT_NUMBER_LENGTH
                ),
                Arguments.of(
                        "digit is null",
                        new AccountOperationRequestDTO("99999", null, amount),
                        "digit",
                        ValidationMessages.REQUIRED_ACCOUNT_DIGIT
                ),
                Arguments.of(
                        "digit does not have 1 digit",
                        new AccountOperationRequestDTO("99999", "12", amount),
                        "digit",
                        ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH
                ),
                Arguments.of(
                        "amount is null",
                        new AccountOperationRequestDTO("99999", "5", null),
                        "amount",
                        ValidationMessages.REQUIRED_OPERATION_AMOUNT
                ),
                Arguments.of(
                        "amount is zero",
                        new AccountOperationRequestDTO(
                                "99999",
                                "5",
                                BigDecimal.ZERO
                        ),
                        "amount",
                        ValidationMessages.POSITIVE_OPERATION_AMOUNT
                ),
                Arguments.of(
                        "amount is negative",
                        new AccountOperationRequestDTO(
                                "99999",
                                "5",
                                new BigDecimal("-1.00")
                        ),
                        "amount",
                        ValidationMessages.POSITIVE_OPERATION_AMOUNT
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
                        ValidationMessages.REQUIRED_SOURCE_ACCOUNT_NUMBER
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
                        ValidationMessages.INVALID_ACCOUNT_NUMBER_LENGTH
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
                        ValidationMessages.REQUIRED_SOURCE_ACCOUNT_DIGIT
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
                        ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH
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
                        ValidationMessages.REQUIRED_DESTINATION_ACCOUNT_NUMBER
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
                        ValidationMessages.INVALID_ACCOUNT_NUMBER_LENGTH
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
                        ValidationMessages.REQUIRED_DESTINATION_ACCOUNT_DIGIT
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
                        ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH
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
                        ValidationMessages.REQUIRED_TRANSFER_AMOUNT
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
                        ValidationMessages.POSITIVE_TRANSFER_AMOUNT
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
                        ValidationMessages.POSITIVE_TRANSFER_AMOUNT
                )
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
                        .header(IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY.toString())
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
                                .header(IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ApiErrorMessages.VALIDATION_ERROR))
                .andExpect(jsonPath("$.errors." + field).value(message));
    }

    private void performMissingBody(String url) throws Exception {
        mockMvc.perform(
                        post(url)
                                .header(IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ApiErrorMessages.INVALID_REQUEST_DATA));
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
        assertEquals(expected.transferCode(), actual.transferCode());
        assertEquals(expected.type(), actual.type());
        assertEquals(
                0,
                expected.amount().compareTo(actual.amount())
        );
        assertEquals(expected.createdAt(), actual.createdAt());
        assertEquals(expected.yieldDate(), actual.yieldDate());
        assertEquals(
                0,
                expected.balanceAfter().compareTo(actual.balanceAfter())
        );
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
                type == TransactionType.TRANSFER_SENT
                        ? UUID.randomUUID()
                        : null,
                type,
                new BigDecimal("100.00"),
                Instant.parse("2026-01-15T13:30:45Z"),
                null,
                new BigDecimal("100.00")
        );
    }
}
