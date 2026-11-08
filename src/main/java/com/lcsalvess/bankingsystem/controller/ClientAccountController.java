package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.response.account.AccountResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.util.pagination.SortWhitelist;
import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(
        name = "Client Accounts",
        description = "Bank account operations associated with a specific client."
)
@PreAuthorize("hasRole('EMPLOYEE')")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/accounts")
public class ClientAccountController {

    private static final String SORTABLE = "accountNumber,balance,status,type";

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(SORTABLE.split(","));

    private final AccountService accountService;

    public ClientAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(
            summary = "Get accounts of a client",
            description = """
                    Retrieves the accounts of a client using pagination and
                    sorting, with optional filters by account type and status.
                    Cancelled accounts are included unless filtered out.
                    The default page size is 10, and the default sort order
                    is ascending by account number. The maximum page size is 100.
                    """
    )
    @SortableBy(
            value = SORTABLE,
            defaultSort = "accountNumber,ASC"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Accounts retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid client ID or request parameters"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found"
            )
    })
    @GetMapping
    public Page<AccountResponseDTO> findByClientId(
            @Parameter(description = "Client unique identifier", example = "1")
            @PathVariable Long clientId,
            @Parameter(description = "Filter accounts by type", example = "SAVINGS")
            @RequestParam(required = false) AccountType type,
            @Parameter(description = "Filter accounts by status", example = "ACTIVE")
            @RequestParam(required = false) AccountStatus status,
            @ParameterObject
            @PageableDefault(size = 10, sort = "accountNumber")
            Pageable pageable
    ) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        return accountService.findByClientId(clientId, type, status, pageable);
    }
}
