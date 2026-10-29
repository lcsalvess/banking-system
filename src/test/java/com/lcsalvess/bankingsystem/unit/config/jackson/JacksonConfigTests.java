package com.lcsalvess.bankingsystem.unit.config.jackson;

import com.lcsalvess.bankingsystem.config.jackson.JacksonConfig;
import com.lcsalvess.bankingsystem.controller.TransactionController;
import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slices do not scan {@code @Configuration} classes, so {@link JacksonConfig}
 * is imported explicitly to check the strict JSON coercion rules end to end.
 */
@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        JacksonConfig.class,
        WebMvcTestSecurityConfig.class
})
class JacksonConfigTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.config.jackson.JacksonConfigTests#";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final String VALID_BODY =
            "{\"accountNumber\": \"99999\", \"digit\": \"5\", \"amount\": 100.00}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionQueryService transactionQueryService;

    @MockitoBean
    private TransactionIdempotencyService transactionIdempotencyService;

    @Nested
    @DisplayName("POST /api/v1/transactions/deposit")
    class Deposit {

        private static final String URL = "/api/v1/transactions/deposit";

        @Test
        @DisplayName("Should accept strings for identifiers and a number for the amount")
        void shouldAcceptStringsForIdentifiersAndANumberForTheAmount() throws Exception {
            when(transactionIdempotencyService.deposit(
                    any(UUID.class),
                    any(AccountOperationRequestDTO.class)
            )).thenReturn(new TransactionResponseDTO(
                    UUID.randomUUID(),
                    null,
                    TransactionType.DEPOSIT,
                    new BigDecimal("100.00"),
                    Instant.parse("2026-01-15T13:30:45Z"),
                    null,
                    new BigDecimal("100.00")
            ));

            mockMvc.perform(post(URL)
                            .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated());

            verify(transactionIdempotencyService).deposit(
                    any(UUID.class),
                    any(AccountOperationRequestDTO.class)
            );
            verifyNoMoreInteractions(transactionIdempotencyService);
            verifyNoInteractions(transactionQueryService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "wronglyTypedBodies")
        @DisplayName("Should return 400 when a field has the wrong JSON type")
        void shouldReturnBadRequestWhenAFieldHasTheWrongJsonType(String scenario, String body) throws Exception {
            mockMvc.perform(post(URL)
                            .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(ApiErrorMessages.INVALID_REQUEST_DATA));

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when the Idempotency-Key header is missing")
        void shouldReturnBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value(ApiErrorMessages.MISSING_REQUIRED_PARAMETER));

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }

        @Test
        @DisplayName("Should return 400 when the Idempotency-Key header is not a valid UUID")
        void shouldReturnBadRequestWhenIdempotencyKeyIsNotAUuid() throws Exception {
            mockMvc.perform(post(URL)
                            .header(IDEMPOTENCY_HEADER, "not-a-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value(ApiErrorMessages.INVALID_REQUEST_PARAMETER));

            verifyNoInteractions(transactionIdempotencyService, transactionQueryService);
        }
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> wronglyTypedBodies() {
        return Stream.of(
                Arguments.of("account number sent as an integer",
                        "{\"accountNumber\": 99999, \"digit\": \"5\", \"amount\": 100.00}"),
                Arguments.of("account number sent as a decimal",
                        "{\"accountNumber\": 9999.9, \"digit\": \"5\", \"amount\": 100.00}"),
                Arguments.of("digit sent as a boolean",
                        "{\"accountNumber\": \"99999\", \"digit\": true, \"amount\": 100.00}"),
                Arguments.of("amount sent as a string",
                        "{\"accountNumber\": \"99999\", \"digit\": \"5\", \"amount\": \"100.00\"}")
        );
    }
}