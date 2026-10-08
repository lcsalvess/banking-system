package com.lcsalvess.bankingsystem.unit.config;

import com.lcsalvess.bankingsystem.config.JacksonConfig;
import com.lcsalvess.bankingsystem.controller.TransactionController;
import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
            "com.lcsalvess.bankingsystem.unit.config.JacksonConfigTests#";

    private static final String INVALID_BODY_MESSAGE =
            "Dados da requisição inválidos.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Nested
    @DisplayName("POST /api/v1/transactions/deposit")
    class Deposit {

        private static final String URL = "/api/v1/transactions/deposit";

        @Test
        @DisplayName("Should accept strings for identifiers and a number for the amount")
        void shouldAcceptStringsForIdentifiersAndANumberForTheAmount() throws Exception {
            when(transactionService.deposit(any(AccountOperationRequestDTO.class)))
                    .thenReturn(new TransactionResponseDTO(
                            UUID.randomUUID(),
                            null,
                            TransactionType.DEPOSIT,
                            new BigDecimal("100.00"),
                            LocalDateTime.of(2026, 1, 15, 10, 30, 45)
                    ));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountNumber\": \"99999\", \"digit\": \"5\", \"amount\": 100.00}"))
                    .andExpect(status().isCreated());

            verify(transactionService).deposit(any(AccountOperationRequestDTO.class));
            verifyNoMoreInteractions(transactionService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "wronglyTypedBodies")
        @DisplayName("Should return 400 when a field has the wrong JSON type")
        void shouldReturnBadRequestWhenAFieldHasTheWrongJsonType(String scenario, String body) throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(INVALID_BODY_MESSAGE));

            verifyNoInteractions(transactionService);
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