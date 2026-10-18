package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.account.AccountRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.account.AccountResponseDTO;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.util.pagination.SortWhitelist;
import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountDigit;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountNumber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(
        name = "Accounts",
        description = "Bank account management and operations."
)
@PreAuthorize("hasRole('EMPLOYEE')")
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final String SORTABLE = "accountNumber,balance,status,type";

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(SORTABLE.split(","));

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(
            summary = "Get accounts",
            description = """
                    Retrieves accounts using pagination and sorting.
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
                    description = "Invalid request parameters"
            )
    })
    @GetMapping
    public Page<AccountResponseDTO> findAll(
            @ParameterObject
            @PageableDefault(size = 10, sort = "accountNumber")
            Pageable pageable
    ) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        return accountService.findAll(pageable);
    }

    @Operation(
            summary = "Get account by account number and digit",
            description = "Retrieves an account using its account number and check digit."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Account retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account number or check digit"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found"
            )
    })
    @GetMapping("/{accountNumber}")
    public AccountResponseDTO findByAccountNumber(
            @Parameter(
                    description = "Account number",
                    example = "12345",
                    required = true
            )
            @PathVariable
            @ValidAccountNumber
            String accountNumber,
            @Parameter(
                    description = "Account check digit",
                    example = "1",
                    required = true
            )
            @RequestParam
            @ValidAccountDigit
            String digit
    ) {
        return accountService.findByAccountNumber(accountNumber, digit);
    }

    @Operation(
            summary = "Create an account",
            description = "Creates a new account."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Account created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account data or request format"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Account already exists"
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponseDTO create(@Valid @RequestBody AccountRequestDTO dto) {
        return accountService.create(dto);
    }

    @Operation(
            summary = "Cancel an account",
            description = "Cancels an account using its account number and check digit."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Account cancelled successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account number or check digit"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Account cannot be cancelled due to its current state or remaining balance"
            )
    })
    @PatchMapping("/{accountNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @Parameter(
                    description = "Account number",
                    example = "12345",
                    required = true
            )
            @PathVariable
            @ValidAccountNumber
            String accountNumber,
            @Parameter(
                    description = "Account check digit",
                    example = "1",
                    required = true
            )
            @ValidAccountDigit
            @RequestParam String digit
    ) {
        accountService.cancel(accountNumber, digit);
    }
}
