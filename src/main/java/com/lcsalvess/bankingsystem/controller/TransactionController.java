package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.service.transaction.TransactionQueryService;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Tag(
        name = "Transactions",
        description = "Deposits, withdrawals, transfers and transaction history."
)
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final String SORTABLE = "createdAt,id,type,amount";

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(SORTABLE.split(","));

    private final TransactionQueryService transactionQueryService;
    private final TransactionIdempotencyService transactionIdempotencyService;

    public TransactionController(TransactionQueryService transactionQueryService, TransactionIdempotencyService transactionIdempotencyService) {
        this.transactionQueryService = transactionQueryService;
        this.transactionIdempotencyService = transactionIdempotencyService;
    }

    @Operation(
            summary = "Retrieve transactions by account",
            description = "Retrieves transactions for the specified account, with pagination and optional filters."
    )
    @SortableBy(
            value = SORTABLE,
            defaultSort = "createdAt,DESC;id,DESC"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters or account identifiers"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found"
            )
    })
    @PreAuthorize("hasRole('EMPLOYEE')")
    @GetMapping("/accounts/{accountNumber}")
    public Page<TransactionResponseDTO> findByAccountNumber(
            @Parameter(
                    description = "Account number",
                    example = "12345",
                    required = true
            )
            @ValidAccountNumber
            @PathVariable String accountNumber,

            @Parameter(
                    description = "Account check digit",
                    example = "1",
                    required = true
            )
            @ValidAccountDigit
            @RequestParam String digit,

            @Parameter(description = "Filter by transaction type")
            @RequestParam(required = false)
            TransactionType type,

            @Parameter(
                    description = "Start date (inclusive)",
                    example = "2026-01-01"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @Parameter(
                    description = "End date (inclusive)",
                    example = "2026-01-31"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @ParameterObject
            @PageableDefault(
                    size = 10,
                    sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        return transactionQueryService.findByAccountNumber(
                accountNumber,
                digit,
                type,
                startDate,
                endDate,
                pageable
        );
    }

    @Operation(
            summary = "Retrieve a transaction by its public identifier",
            description = "Retrieves a transaction using its unique public UUID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transaction UUID format"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transaction not found"
            )
    })
    @PreAuthorize("hasRole('EMPLOYEE')")
    @GetMapping("/code/{transactionCode}")
    public TransactionResponseDTO findByTransactionCode(
            @PathVariable UUID transactionCode
    ) {
        return transactionQueryService.findByTransactionCode(transactionCode);
    }

    @Operation(
            summary = "Deposit funds into an account",
            description = "Deposits the specified amount into an active account and records the deposit transaction"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Deposit completed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request, missing Idempotency-Key header or invalid UUID format"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Account is not active or the idempotency key was reused with a different operation or payload"
            )
    })
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO deposit(
            @Parameter(
                    description = "UUID usado para identificar esta operação. "
                            + "Reutilize a mesma chave ao repetir a mesma requisição.",
                    example = "8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210",
                    required = true
            )
            @RequestHeader(name = "Idempotency-Key", required = true)
            UUID idempotencyKey,

            @Valid @RequestBody AccountOperationRequestDTO dto
    ) {
        return transactionIdempotencyService.deposit(idempotencyKey, dto);
    }

    @Operation(
            summary = "Withdraw funds from an account",
            description = "Withdraws the specified amount from the account and records the transaction."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Withdrawal completed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid withdrawal request, missing Idempotency-Key header or invalid UUID format"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Account is not active, has insufficient funds, or the idempotency key was reused with a different operation or payload"
            )
    })
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO withdraw(
            @Parameter(
                    description = "UUID usado para identificar esta operação. "
                            + "Reutilize a mesma chave ao repetir a mesma requisição.",
                    example = "8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210",
                    required = true
            )
            @RequestHeader(name = "Idempotency-Key", required = true)
            UUID idempotencyKey,

            @Valid @RequestBody AccountOperationRequestDTO dto
    ) {
        return transactionIdempotencyService.withdraw(idempotencyKey, dto);
    }

    @Operation(
            summary = "Transfer funds between accounts",
            description = "Transfers the specified amount from the source account to the destination account and records both transfer transactions."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Transfer completed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transfer request or account identifier, missing Idempotency-Key header or invalid UUID format"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Source or destination account not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Account is not active, has insufficient funds, or the idempotency key was reused with a different operation or payload"
            )
    })
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO transfer(
            @Parameter(
                    description = "UUID usado para identificar esta operação. "
                            + "Reutilize a mesma chave ao repetir a mesma requisição.",
                    example = "8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210",
                    required = true
            )
            @RequestHeader(name = "Idempotency-Key", required = true)
            UUID idempotencyKey,

            @Valid @RequestBody TransferRequestDTO dto
    ) {
        return transactionIdempotencyService.transfer(idempotencyKey, dto);
    }
}