package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import com.lucas.bankingsystem.entity.enums.AccountType;
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
                    .andExpect(jsonPath("$[0].type").value(account1.type().toString()))
                    .andExpect(jsonPath("$[0].status").value(account1.status().toString()))
                    .andExpect(jsonPath("$[1].accountNumber").value(account2.accountNumber()))
                    .andExpect(jsonPath("$[1].accountDigit").value(account2.accountDigit()))
                    .andExpect(jsonPath("$[1].clientName").value(account2.clientName()))
                    .andExpect(jsonPath("$[1].type").value(account2.type().toString()))
                    .andExpect(jsonPath("$[1].status").value(account2.status().toString()))
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
            AccountResponseDTO account = mockAccount("99999",
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
                    .andExpect(jsonPath("$.type").value(account.type().toString()))
                    .andExpect(jsonPath("$.status").value(account.status().toString()))
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
