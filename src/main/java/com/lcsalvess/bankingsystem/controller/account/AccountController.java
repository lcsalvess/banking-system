package com.lcsalvess.bankingsystem.controller.account;

import com.lcsalvess.bankingsystem.dto.request.account.AccountRequestDto;
import com.lcsalvess.bankingsystem.dto.response.account.AccountResponseDto;
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
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller responsible for bank account management and retrieval.
 */
@Tag(
    name = "Accounts",
    description =
        "Bank account management and retrieval, including accounts "
            + "associated with a client."
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

  /**
   * Creates the account controller.
   *
   * @param accountService the service responsible for account operations
   */
  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  /**
   * Retrieves accounts using pagination and sorting.
   *
   * @param pageable pagination and sorting parameters
   * @return a page of account response DTOs
   */
  @Operation(
      summary = "Get accounts",
      description =
      """
      Retrieves accounts using pagination and sorting.
      The default page size is 10, with accounts sorted by account number
      in ascending order. The maximum page size is 100.
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
  public Page<AccountResponseDto> findAll(
      @ParameterObject
      @PageableDefault(sort = "accountNumber")
      Pageable pageable
  ) {
    SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

    return accountService.findAll(pageable);
  }

  /**
   * Retrieves an account using its account number and check digit.
   *
   * @param accountNumber the account number
   * @param digit         the account verification digit
   * @return the account response DTO
   */
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
  public AccountResponseDto findByAccountNumber(
      @Parameter(description = "Account number", example = "12345", required = true)
      @PathVariable
      @ValidAccountNumber
      String accountNumber,
      @Parameter(description = "Account check digit", example = "1", required = true)
      @RequestParam
      @ValidAccountDigit
      String digit
  ) {
    return accountService.findByAccountNumber(accountNumber, digit);
  }

  /**
   * Creates an account if the client does not already have an active
   * account of the requested type.
   *
   * @param dto the account creation data
   * @return the created account response DTO
   */
  @Operation(
      summary = "Create an account",
      description =
      "Creates an account if the client does not already have "
      + "an active account of the requested type."
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
          description = "The client already has an active account of this type"
      )
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponseDto create(@Valid @RequestBody AccountRequestDto dto) {
    return accountService.create(dto);
  }

  /**
   * Cancels an account identified by its account number and check digit.
   *
   * @param accountNumber the account number
   * @param digit         the account verification digit
   */
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
          description = "Account cannot be cancelled due to its current state "
              + "or remaining balance"
      )
  })
  @PatchMapping("/{accountNumber}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(
      @Parameter(description = "Account number", example = "12345", required = true)
      @PathVariable
      @ValidAccountNumber
      String accountNumber,
      @Parameter(description = "Account check digit", example = "1", required = true)
      @ValidAccountDigit
      @RequestParam
      String digit
  ) {
    accountService.cancel(accountNumber, digit);
  }
}
