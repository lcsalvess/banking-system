package com.lcsalvess.bankingsystem.unit.controller.account;

import com.lcsalvess.bankingsystem.config.pagination.PaginationConfig;
import com.lcsalvess.bankingsystem.controller.account.ClientAccountController;
import com.lcsalvess.bankingsystem.dto.response.account.AccountResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcTestSecurityConfig.class,
        PaginationConfig.class
})
public class ClientAccountControllerTests {

    private static final String URL = "/api/v1/clients/{clientId}/accounts";
    private static final Long CLIENT_ID = 1L;
    private static final String CLIENT_NAME = "Cliente Teste";

    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(
            0,
            10,
            Sort.by("accountNumber").ascending()
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Nested
    @DisplayName("GET /api/v1/clients/{clientId}/accounts")
    class FindByClientId {

        @Test
        @DisplayName("Should return a page of the client's accounts successfully")
        void shouldReturnPageOfAccountsSuccessfully() throws Exception {
            AccountResponseDTO checking = account(
                    "00001", "9", AccountType.CHECKING, AccountStatus.ACTIVE
            );
            AccountResponseDTO savings = account(
                    "00002", "7", AccountType.SAVINGS, AccountStatus.CANCELLED
            );

            Page<AccountResponseDTO> page = new PageImpl<>(
                    List.of(checking, savings),
                    DEFAULT_PAGEABLE,
                    2
            );

            when(accountService.findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE))
                    .thenReturn(page);

            mockMvc.perform(get(URL, CLIENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].accountNumber").value("00001"))
                    .andExpect(jsonPath("$.content[0].accountDigit").value("9"))
                    .andExpect(jsonPath("$.content[0].clientName").value(CLIENT_NAME))
                    .andExpect(jsonPath("$.content[0].type").value("CHECKING"))
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.content[1].accountNumber").value("00002"))
                    .andExpect(jsonPath("$.content[1].type").value("SAVINGS"))
                    .andExpect(jsonPath("$.content[1].status").value("CANCELLED"))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));

            verify(accountService).findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return an empty page when the client has no accounts")
        void shouldReturnEmptyPageWhenClientHasNoAccounts() throws Exception {
            Page<AccountResponseDTO> page = new PageImpl<>(
                    List.of(),
                    DEFAULT_PAGEABLE,
                    0
            );

            when(accountService.findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE))
                    .thenReturn(page);

            mockMvc.perform(get(URL, CLIENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.totalElements").value(0))
                    .andExpect(jsonPath("$.page.totalPages").value(0));

            verify(accountService).findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should pass the type and status filters to the service")
        void shouldPassFiltersToService() throws Exception {
            AccountResponseDTO savings = account(
                    "00002", "7", AccountType.SAVINGS, AccountStatus.ACTIVE
            );

            when(accountService.findByClientId(
                    CLIENT_ID,
                    AccountType.SAVINGS,
                    AccountStatus.ACTIVE,
                    DEFAULT_PAGEABLE
            )).thenReturn(new PageImpl<>(List.of(savings), DEFAULT_PAGEABLE, 1));

            mockMvc.perform(get(URL, CLIENT_ID)
                            .param("type", "SAVINGS")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].type").value("SAVINGS"))
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

            verify(accountService).findByClientId(
                    CLIENT_ID,
                    AccountType.SAVINGS,
                    AccountStatus.ACTIVE,
                    DEFAULT_PAGEABLE
            );
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should pass the requested page, size and sort to the service")
        void shouldPassPaginationAndSortToService() throws Exception {
            Pageable requested = PageRequest.of(2, 5, Sort.by("balance").descending());

            when(accountService.findByClientId(CLIENT_ID, null, null, requested))
                    .thenReturn(new PageImpl<>(List.of(), requested, 0));

            mockMvc.perform(get(URL, CLIENT_ID)
                            .param("page", "2")
                            .param("size", "5")
                            .param("sort", "balance,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));

            verify(accountService).findByClientId(CLIENT_ID, null, null, requested);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should cap the page size at 100")
        void shouldCapPageSizeAtMaximum() throws Exception {
            Pageable capped = PageRequest.of(0, 100, Sort.by("accountNumber").ascending());

            when(accountService.findByClientId(CLIENT_ID, null, null, capped))
                    .thenReturn(new PageImpl<>(List.of(), capped, 0));

            mockMvc.perform(get(URL, CLIENT_ID).param("size", "500"))
                    .andExpect(status().isOk());

            verify(accountService).findByClientId(CLIENT_ID, null, null, capped);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when the client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            when(accountService.findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE))
                    .thenThrow(new ClientNotFoundException());

            performErrorResponse(
                    mockMvc.perform(get(URL, CLIENT_ID)),
                    404,
                    ApiErrorMessages.CLIENT_NOT_FOUND
            );

            verify(accountService).findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE);
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 400 when the client ID is invalid")
        void shouldReturnBadRequestWhenClientIdIsInvalid() throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(URL, "abc")),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(accountService);
        }

        @ParameterizedTest(name = "{0}={1}")
        @CsvSource({
                "type,CREDIT",
                "status,CLOSED"
        })
        @DisplayName("Should return 400 when a filter value is invalid")
        void shouldReturnBadRequestWhenFilterValueIsInvalid(
                String parameter,
                String value
        ) throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(URL, CLIENT_ID).param(parameter, value)),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(accountService);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"id", "cpf", "clientName", "unknown"})
        @DisplayName("Should return 400 when the sort property is outside the whitelist")
        void shouldReturnBadRequestWhenSortPropertyIsNotWhitelisted(
                String property
        ) throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(URL, CLIENT_ID).param("sort", property + ",asc")),
                    400,
                    ApiErrorMessages.INVALID_SORT_PROPERTY
            );

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 500 when the service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(accountService.findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE))
                    .thenThrow(new RuntimeException());

            performErrorResponse(
                    mockMvc.perform(get(URL, CLIENT_ID)),
                    500,
                    ApiErrorMessages.INTERNAL_SERVER_ERROR
            );

            verify(accountService).findByClientId(CLIENT_ID, null, null, DEFAULT_PAGEABLE);
            verifyNoMoreInteractions(accountService);
        }
    }

    private static AccountResponseDTO account(
            String number,
            String digit,
            AccountType type,
            AccountStatus status
    ) {
        return new AccountResponseDTO(
                number,
                digit,
                CLIENT_NAME,
                BigDecimal.ZERO,
                type,
                status
        );
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
}
