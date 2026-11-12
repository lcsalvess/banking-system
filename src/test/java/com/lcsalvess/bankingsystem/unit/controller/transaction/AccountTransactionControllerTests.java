package com.lcsalvess.bankingsystem.unit.controller.transaction;

import com.lcsalvess.bankingsystem.config.pagination.PaginationConfig;
import com.lcsalvess.bankingsystem.controller.transaction.AccountTransactionController;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.exception.account.AccountNotFoundException;
import com.lcsalvess.bankingsystem.exception.account.InvalidAccountDigitException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionPageResult;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountTransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcTestSecurityConfig.class,
        PaginationConfig.class
})
@DisplayName("AccountTransactionController")
class AccountTransactionControllerTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.controller.transaction.AccountTransactionControllerTests#";

    private static final String SNAPSHOT_HEADER = "X-Transaction-Snapshot";

    private static final String SNAPSHOT_TOKEN =
            "v1:123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionQueryService transactionQueryService;

    @Nested
    @DisplayName("GET /api/v1/accounts/{accountNumber}/transactions")
    class FindByAccountNumber {

        private static final String URL =
                "/api/v1/accounts/{accountNumber}/transactions";

        private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(
                0, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );

        @Test
        @DisplayName("Should return the page of account transactions successfully")
        void shouldReturnThePageOfAccountTransactionsSuccessfully() throws Exception {
            TransactionResponseDTO transaction1 =
                    mockTransaction(TransactionType.DEPOSIT);

            TransactionResponseDTO transaction2 =
                    mockTransaction(TransactionType.WITHDRAWAL);

            stubService(new PageImpl<>(
                    List.of(transaction1, transaction2), DEFAULT_PAGEABLE, 2
            ));

            MvcResult result = mockMvc.perform(get(URL, "99999")
                            .param("digit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1))
                    .andExpect(header().string(SNAPSHOT_HEADER, SNAPSHOT_TOKEN))
                    .andReturn();

            TransactionResponseDTO[] content = readPageContent(result);

            assertTransactionEquals(transaction1, content[0]);
            assertTransactionEquals(transaction2, content[1]);

            Pageable pageable = verifyServiceCall(null, null, null);

            assertEquals(DEFAULT_PAGEABLE, pageable);
        }

        @Test
        @DisplayName("Should return empty page when the account has no transactions")
        void shouldReturnEmptyPageWhenAccountHasNoTransactions() throws Exception {
            stubService(new PageImpl<>(List.of(), DEFAULT_PAGEABLE, 0));

            mockMvc.perform(get(URL, "99999")
                            .param("digit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.totalElements").value(0))
                    .andExpect(jsonPath("$.page.totalPages").value(0));

            verifyServiceCall(null, null, null);
        }

        @Test
        @DisplayName("Should forward type and date filters to the service")
        void shouldForwardFiltersToTheService() throws Exception {
            stubService(new PageImpl<>(List.of(), DEFAULT_PAGEABLE, 0));

            mockMvc.perform(get(URL, "99999")
                            .param("digit", "5")
                            .param("type", "DEPOSIT")
                            .param("startDate", "2026-01-01")
                            .param("endDate", "2026-01-31"))
                    .andExpect(status().isOk());

            verifyServiceCall(
                    TransactionType.DEPOSIT,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31)
            );
        }

        @Test
        @DisplayName("Should forward custom page, size and sort to the service")
        void shouldForwardCustomPagination() throws Exception {
            stubService(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

            mockMvc.perform(get(URL, "99999")
                            .param("digit", "5")
                            .param("page", "2")
                            .param("size", "5")
                            .param("sort", "amount,asc"))
                    .andExpect(status().isOk());

            Pageable pageable = verifyServiceCall(null, null, null);

            assertEquals(
                    PageRequest.of(2, 5, Sort.by(Sort.Direction.ASC, "amount")),
                    pageable
            );
        }

        @Test
        @DisplayName("Should limit the page size to the configured maximum")
        void shouldLimitThePageSizeToTheMaximum() throws Exception {
            stubService(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

            mockMvc.perform(get(URL, "99999")
                            .param("digit", "5")
                            .param("size", "1000"))
                    .andExpect(status().isOk());

            Pageable pageable = verifyServiceCall(null, null, null);

            assertEquals(100, pageable.getPageSize());
        }

        @Test
        @DisplayName("Should return 400 when digit is invalid")
        void shouldReturnBadRequestWhenDigitIsInvalid() throws Exception {
            when(transactionQueryService.findByAccountNumber(
                    eq("99999"), eq("3"), any(), any(), any(), any(Pageable.class), isNull()
            )).thenThrow(
                    new InvalidAccountDigitException(
                            ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH
                    )
            );

            performGet("3", 400, ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH);

            verify(transactionQueryService).findByAccountNumber(
                    eq("99999"), eq("3"), isNull(), isNull(), isNull(),
                    any(Pageable.class), isNull()
            );
            verifyNoMoreInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
            when(transactionQueryService.findByAccountNumber(
                    eq("99999"), eq("5"), any(), any(), any(), any(Pageable.class), isNull()
            )).thenThrow(
                    new AccountNotFoundException(ApiErrorMessages.ACCOUNT_NOT_FOUND)
            );

            performGet("5", 404, ApiErrorMessages.ACCOUNT_NOT_FOUND);

            verifyServiceCall(null, null, null);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(transactionQueryService.findByAccountNumber(
                    eq("99999"), eq("5"), any(), any(), any(),
                    any(Pageable.class), isNull()
            )).thenThrow(new RuntimeException());

            performGet("5", 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verifyServiceCall(null, null, null);
        }

        @Test
        @DisplayName("Should return 400 when digit is missing")
        void shouldReturnBadRequestWhenDigitIsMissing() throws Exception {
            mockMvc.perform(get(URL, "99999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(ApiErrorMessages.MISSING_REQUIRED_PARAMETER));

            verifyNoInteractions(transactionQueryService);
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
                    .andExpect(jsonPath("$.message").value(ApiErrorMessages.VALIDATION_ERROR))
                    .andExpect(
                            jsonPath("$.errors.accountNumber")
                                    .value(ValidationMessages.INVALID_ACCOUNT_NUMBER_LENGTH)
                    );

            verifyNoInteractions(transactionQueryService);
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
                    .andExpect(jsonPath("$.message").value(ApiErrorMessages.VALIDATION_ERROR))
                    .andExpect(
                            jsonPath("$.errors.digit")
                                    .value(ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH)
                    );

            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when transaction type is invalid")
        void shouldReturnBadRequestWhenTypeIsInvalid() throws Exception {
            ResultActions result = mockMvc.perform(get(URL, "99999")
                    .param("digit", "5")
                    .param("type", "INVALID"));

            performErrorResponse(result, 400, ApiErrorMessages.INVALID_REQUEST_PARAMETER);

            verifyNoInteractions(transactionQueryService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidDateParameters")
        @DisplayName("Should return 400 when a date parameter is invalid")
        void shouldReturnBadRequestWhenDateIsInvalid(
                String scenario,
                String parameter,
                String value
        ) throws Exception {
            ResultActions result = mockMvc.perform(get(URL, "99999")
                    .param("digit", "5")
                    .param(parameter, value));

            performErrorResponse(result, 400, ApiErrorMessages.INVALID_REQUEST_PARAMETER);

            verifyNoInteractions(transactionQueryService);
        }

        @Test
        @DisplayName("Should forward snapshot token to the service")
        void shouldForwardSnapshotTokenToService() throws Exception {
            Page<TransactionResponseDTO> page =
                    new PageImpl<>(List.of(), DEFAULT_PAGEABLE, 0);

            when(transactionQueryService.findByAccountNumber(
                    eq("99999"), eq("5"), any(), any(), any(),
                    any(Pageable.class), eq(SNAPSHOT_TOKEN)
            )).thenReturn(new TransactionPageResult(page, SNAPSHOT_TOKEN));

            mockMvc.perform(get(URL, "99999")
                            .param("digit", "5")
                            .header(SNAPSHOT_HEADER, SNAPSHOT_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(header().string(SNAPSHOT_HEADER, SNAPSHOT_TOKEN));

            verify(transactionQueryService).findByAccountNumber(
                    eq("99999"), eq("5"), isNull(), isNull(), isNull(),
                    any(Pageable.class), eq(SNAPSHOT_TOKEN)
            );
            verifyNoMoreInteractions(transactionQueryService);
        }

        private void stubService(Page<TransactionResponseDTO> page) {
            when(transactionQueryService.findByAccountNumber(
                    eq("99999"),
                    eq("5"),
                    any(),
                    any(),
                    any(),
                    any(Pageable.class),
                    isNull()
            )).thenReturn(new TransactionPageResult(page, SNAPSHOT_TOKEN));
        }

        private Pageable verifyServiceCall(
                TransactionType type,
                LocalDate startDate,
                LocalDate endDate
        ) {
            ArgumentCaptor<Pageable> pageableCaptor =
                    ArgumentCaptor.forClass(Pageable.class);

            verify(transactionQueryService).findByAccountNumber(
                    eq("99999"),
                    eq("5"),
                    eq(type),
                    eq(startDate),
                    eq(endDate),
                    pageableCaptor.capture(),
                    isNull()
            );
            verifyNoMoreInteractions(transactionQueryService);

            return pageableCaptor.getValue();
        }

        private TransactionResponseDTO[] readPageContent(MvcResult result)
                throws Exception {
            JsonNode root = objectMapper.readTree(
                    result.getResponse().getContentAsString()
            );

            return objectMapper.treeToValue(
                    root.get("content"),
                    TransactionResponseDTO[].class
            );
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
    static Stream<Arguments> invalidDateParameters() {
        return Stream.of(
                Arguments.of("start date has invalid month", "startDate", "2026-13-01"),
                Arguments.of("start date is not in ISO format", "startDate", "01/01/2026"),
                Arguments.of("end date has invalid month", "endDate", "2026-00-10"),
                Arguments.of("end date is not a date", "endDate", "abc")
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
