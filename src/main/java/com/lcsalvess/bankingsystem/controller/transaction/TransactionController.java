package com.lcsalvess.bankingsystem.controller.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyService;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(
        name = "Transactions",
        description = "Deposits, withdrawals, transfers and transaction history."
)
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionQueryService transactionQueryService;
    private final TransactionIdempotencyService transactionIdempotencyService;

    public TransactionController(TransactionQueryService transactionQueryService, TransactionIdempotencyService transactionIdempotencyService) {
        this.transactionQueryService = transactionQueryService;
        this.transactionIdempotencyService = transactionIdempotencyService;
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
    @GetMapping("/{transactionCode}")
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
    @PostMapping("/deposits")
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
    @PostMapping("/withdrawals")
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
    @PostMapping("/transfers")
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
