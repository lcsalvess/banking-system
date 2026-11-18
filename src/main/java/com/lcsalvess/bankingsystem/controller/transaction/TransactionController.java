package com.lcsalvess.bankingsystem.controller.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDto;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDto;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDto;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyService;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
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

/**
 * REST controller for retrieving transactions and processing account
 * deposits, withdrawals, and transfers.
 */
@Tag(
    name = "Transactions",
    description = "Deposits, withdrawals, transfers and transaction history."
)
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

  // Restricts the lookup path variable to the UUID format. Without it, the
  // variable would also capture literal segments such as "deposits", so a
  // GET on a POST-only route would answer 400 instead of 405.
  private static final String UUID_PATH =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
          + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private final TransactionQueryService transactionQueryService;
  private final TransactionIdempotencyService transactionIdempotencyService;

  public TransactionController(
      TransactionQueryService transactionQueryService,
      TransactionIdempotencyService transactionIdempotencyService
  ) {
    this.transactionQueryService = transactionQueryService;
    this.transactionIdempotencyService = transactionIdempotencyService;
  }

  /**
   * Retrieves a transaction by its public identifier.
   *
   * @param transactionCode the public UUID identifying the transaction
   * @return the transaction details
   */
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
          responseCode = "404",
          description = "Transaction not found, or the identifier is not a valid UUID"
      )
  })
  @PreAuthorize("hasRole('EMPLOYEE')")
  @GetMapping("/{transactionCode:" + UUID_PATH + "}")
  public TransactionResponseDto findByTransactionCode(
      @PathVariable UUID transactionCode
  ) {
    return transactionQueryService.findByTransactionCode(transactionCode);
  }

  /**
   * Deposits funds into an active account using an idempotency key.
   *
   * @param idempotencyKey the unique key identifying the operation
   * @param dto            the deposit request data
   * @return the resulting transaction details
   */
  @Operation(
      summary = "Deposit funds into an account",
      description = "Deposits the specified amount into an active account "
          + "and records the deposit transaction."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Deposit completed successfully"
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid request, missing Idempotency-Key header "
              + "or invalid UUID format"
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Account not found"
      ),
      @ApiResponse(
          responseCode = "409",
          description = "Account is not active or the idempotency key was "
              + "reused with a different operation or payload"
      )
  })
  @PreAuthorize("hasRole('EMPLOYEE')")
  @PostMapping("/deposits")
  @ResponseStatus(HttpStatus.CREATED)
  public TransactionResponseDto deposit(
      @Parameter(
          description = "UUID used to identify this operation. "
              + "Reuse the same key when retrying the same request.",
          example = "8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210",
          required = true
      )
      @RequestHeader(name = "Idempotency-Key")
      UUID idempotencyKey,

      @Valid @RequestBody AccountOperationRequestDto dto
  ) {
    return transactionIdempotencyService.deposit(idempotencyKey, dto);
  }

  /**
   * Withdraws funds from an account using an idempotency key.
   *
   * @param idempotencyKey the unique key identifying the operation
   * @param dto the withdrawal request data
   * @return the resulting transaction details
   */
  @Operation(
      summary = "Withdraw funds from an account",
      description = "Withdraws the specified amount from the account "
          + "and records the transaction."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Withdrawal completed successfully"
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid withdrawal request, missing Idempotency-Key "
              + "header or invalid UUID format"
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Account not found"
      ),
      @ApiResponse(
          responseCode = "409",
          description = "Account is not active, has insufficient funds, "
              + "or the idempotency key was reused with a different "
              + "operation or payload"
      )
  })
  @PreAuthorize("hasRole('EMPLOYEE')")
  @PostMapping("/withdrawals")
  @ResponseStatus(HttpStatus.CREATED)
  public TransactionResponseDto withdraw(
      @Parameter(
          description = "UUID used to identify this operation. "
              + "Reuse the same key when retrying the same request.",
          example = "8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210",
          required = true
      )
      @RequestHeader(name = "Idempotency-Key")
      UUID idempotencyKey,

      @Valid @RequestBody AccountOperationRequestDto dto
  ) {
    return transactionIdempotencyService.withdraw(idempotencyKey, dto);
  }

  /**
   * Transfers funds between accounts using an idempotency key.
   *
   * @param idempotencyKey the unique key identifying the operation
   * @param dto the transfer request data
   * @return the resulting transaction details
   */
  @Operation(
      summary = "Transfer funds between accounts",
      description = "Transfers the specified amount from the source account "
          + "to the destination account and records both transfer transactions."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Transfer completed successfully"
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid transfer request or account identifier, "
              + "missing Idempotency-Key header or invalid UUID format"
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Source or destination account not found"
      ),
      @ApiResponse(
          responseCode = "409",
          description = "Account is not active, has insufficient funds, "
              + "or the idempotency key was reused with a different "
              + "operation or payload"
      )
  })
  @PreAuthorize("hasRole('EMPLOYEE')")
  @PostMapping("/transfers")
  @ResponseStatus(HttpStatus.CREATED)
  public TransactionResponseDto transfer(
      @Parameter(
          description = "UUID used to identify this operation. "
              + "Reuse the same key when retrying the same request.",
          example = "8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210",
          required = true
      )
      @RequestHeader(name = "Idempotency-Key")
      UUID idempotencyKey,

      @Valid @RequestBody TransferRequestDto dto
  ) {
    return transactionIdempotencyService.transfer(idempotencyKey, dto);
  }
}
