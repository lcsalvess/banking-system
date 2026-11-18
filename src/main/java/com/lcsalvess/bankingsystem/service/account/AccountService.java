package com.lcsalvess.bankingsystem.service.account;

import com.lcsalvess.bankingsystem.dto.request.account.AccountRequestDto;
import com.lcsalvess.bankingsystem.dto.response.account.AccountResponseDto;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.event.account.AccountOperationEvent;
import com.lcsalvess.bankingsystem.event.account.AccountOperationType;
import com.lcsalvess.bankingsystem.exception.account.AccountAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.account.AccountHasBalanceException;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountNotFoundException;
import com.lcsalvess.bankingsystem.exception.account.InvalidAccountDigitException;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.repository.specification.AccountSpecifications;
import com.lcsalvess.bankingsystem.service.account.generator.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.generator.GeneratedAccountNumber;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates account lifecycle operations, queries, and transaction-safe account locking.
 */
@Service
public class AccountService {

  private final AccountRepository accountRepository;

  private final AccountNumberGenerator accountNumberGenerator;

  private final ClientService clientService;

  private final CurrentUserService currentUserService;

  private final ApplicationEventPublisher eventPublisher;

  private final Clock clock;

  public AccountService(
      AccountRepository accountRepository,
      AccountNumberGenerator accountNumberGenerator,
      ClientService clientService,
      CurrentUserService currentUserService,
      ApplicationEventPublisher eventPublisher,
      Clock clock
  ) {
    this.accountRepository = accountRepository;
    this.accountNumberGenerator = accountNumberGenerator;
    this.clientService = clientService;
    this.currentUserService = currentUserService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Retrieves all accounts using pagination and deterministic sorting.
   *
   * @param pageable pagination and sorting configuration
   * @return a page of account response DTOs
   */
  @Transactional(readOnly = true)
  public Page<AccountResponseDto> findAll(Pageable pageable) {
    return accountRepository.findAllWithClient(
            PageableTieBreaker.withIdTieBreaker(pageable)
        )
        .map(AccountResponseDto::fromEntity);
  }

  /**
   * Retrieves accounts belonging to a client, optionally filtered by type and status.
   *
   * @param clientId identifier of the client
   * @param type optional account type filter
   * @param status optional account status filter
   * @param pageable pagination and sorting configuration
   * @return a page of matching account response DTOs
   */
  @Transactional(readOnly = true)
  public Page<AccountResponseDto> findByClientId(
      Long clientId,
      AccountType type,
      AccountStatus status,
      Pageable pageable
  ) {
    // Fails with 404 before querying when the client does not exist. The
    // client also stays in the persistence context, so mapping the
    // accounts below does not trigger one extra query per account.
    clientService.findEntityById(clientId);

    return accountRepository.findAll(
            AccountSpecifications.byClientAndFilters(clientId, type, status),
            PageableTieBreaker.withIdTieBreaker(pageable)
        )
        .map(AccountResponseDto::fromEntity);
  }

  /**
   * Retrieves an account entity after validating its account digit.
   *
   * @param accountNumber account number to search for
   * @param accountDigit account check digit to validate
   * @return the matching account entity
   * @throws InvalidAccountDigitException if the account digit is invalid
   * @throws AccountNotFoundException if the account does not exist
   */
  @Transactional(readOnly = true)
  public Account findEntityByAccountNumber(String accountNumber, String accountDigit) {
    validateDigit(accountNumber, accountDigit);
    return accountRepository.findByAccountNumber(accountNumber)
        .orElseThrow(() -> new AccountNotFoundException(ApiErrorMessages.ACCOUNT_NOT_FOUND));
  }

  /**
   * Retrieves an account response after validating its account digit.
   *
   * @param accountNumber account number to search for
   * @param accountDigit account check digit to validate
   * @return the matching account response DTO
   * @throws InvalidAccountDigitException if the account digit is invalid
   * @throws AccountNotFoundException if the account does not exist
   */
  @Transactional(readOnly = true)
  public AccountResponseDto findByAccountNumber(String accountNumber, String accountDigit) {
    validateDigit(accountNumber, accountDigit);
    Account account = accountRepository.findByAccountNumberWithClient(accountNumber)
        .orElseThrow(() -> new AccountNotFoundException(ApiErrorMessages.ACCOUNT_NOT_FOUND));
    return AccountResponseDto.fromEntity(account);
  }

  /**
   * Retrieves and locks an account for update within an existing transaction.
   *
   * @param accountNumber account number to lock
   * @param accountDigit account check digit to validate
   * @return the locked account entity
   * @throws InvalidAccountDigitException if the account digit is invalid
   * @throws AccountNotFoundException if the account does not exist
   * @throws org.springframework.transaction.IllegalTransactionStateException
   *     if no transaction is active
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Account findEntityByAccountNumberForUpdate(
      String accountNumber,
      String accountDigit
  ) {
    return lockAccount(accountNumber, accountDigit);
  }

  /**
   * Creates an account for an existing client and publishes an account creation event.
   *
   * @param dto account creation data
   * @return the created account response DTO
   * @throws AccountAlreadyExistsException if the client already has an active account
   *     of the requested type
   */
  @Transactional
  public AccountResponseDto create(AccountRequestDto dto) {
    Client client = clientService.findEntityById(dto.clientId());

    String username = getCurrentUsername();

    GeneratedAccountNumber accountNumber = accountNumberGenerator.generate();

    Account account = createAccount(dto, client, accountNumber);

    Account savedAccount = saveAccount(account);

    eventPublisher.publishEvent(
        new AccountOperationEvent(savedAccount.getId(), AccountOperationType.CREATED, username)
    );

    return AccountResponseDto.fromEntity(savedAccount);
  }

  /**
   * Cancels an active account that has no remaining balance.
   *
   * @param accountNumber account number to cancel
   * @param accountDigit account check digit to validate
   * @throws InvalidAccountDigitException if the account digit is invalid
   * @throws AccountNotFoundException if the account does not exist
   * @throws AccountIsNotActiveException if the account is not active
   * @throws AccountHasBalanceException if the account has a nonzero balance
   */
  @Transactional
  public void cancel(String accountNumber, String accountDigit) {
    Account account = findEntityByAccountNumberForUpdate(accountNumber, accountDigit);

    validateActiveAccount(account);
    validateAccountHasNoBalance(account);

    String username = getCurrentUsername();

    account.cancel();

    eventPublisher.publishEvent(
        new AccountOperationEvent(account.getId(), AccountOperationType.CANCELLED, username)
    );
  }

  private void validateDigit(String accountNumber, String accountDigit) {
    if (!accountNumberGenerator.isValid(accountNumber, accountDigit)) {
      throw new InvalidAccountDigitException(ApiErrorMessages.INVALID_ACCOUNT_DIGIT);
    }
  }

  private Account createAccount(
      AccountRequestDto dto,
      Client client,
      GeneratedAccountNumber accountNumber
  ) {
    if (dto.type() == AccountType.CHECKING) {
      return new CheckingAccount(
          client,
          accountNumber.number(),
          accountNumber.digit()
      );
    }
    return SavingsAccount.create(
        client,
        accountNumber.number(),
        accountNumber.digit(),
        LocalDate.now(clock)
    );
  }

  private String getDuplicateAccountMessage(AccountType type) {
    return switch (type) {
      case CHECKING -> ApiErrorMessages.CHECKING_ACCOUNT_ALREADY_EXISTS;
      case SAVINGS -> ApiErrorMessages.SAVINGS_ACCOUNT_ALREADY_EXISTS;
    };
  }

  private Account saveAccount(Account account) {
    try {
      return accountRepository.saveAndFlush(account);
    } catch (DataIntegrityViolationException exception) {
      if (DatabaseConstraint.ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE.isViolatedBy(exception)) {
        throw new AccountAlreadyExistsException(
            getDuplicateAccountMessage(account.getType())
        );
      }
      throw exception;
    }
  }

  private Account lockAccount(
      String accountNumber,
      String accountDigit
  ) {
    validateDigit(accountNumber, accountDigit);

    Long accountId = accountRepository
        .lockIdByAccountNumber(accountNumber)
        .orElseThrow(() -> new AccountNotFoundException(
            ApiErrorMessages.ACCOUNT_NOT_FOUND
        ));

    return accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(
            ApiErrorMessages.ACCOUNT_NOT_FOUND
        ));
  }

  /**
   * Locks both accounts involved in a transfer in account-number order.
   *
   * <p>The returned object preserves the original transfer direction, regardless of
   * the order in which the accounts are locked.
   *
   * @param fromAccountNumber source account number
   * @param fromAccountDigit source account check digit
   * @param toAccountNumber destination account number
   * @param toAccountDigit destination account check digit
   * @return both locked accounts in their original source and destination order
   * @throws InvalidAccountDigitException if either account digit is invalid
   * @throws AccountNotFoundException if either account does not exist
   * @throws org.springframework.transaction.IllegalTransactionStateException
   *     if no transaction is active
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public LockedAccounts lockAccountsForTransfer(
      String fromAccountNumber,
      String fromAccountDigit,
      String toAccountNumber,
      String toAccountDigit
  ) {
    boolean fromFirst = fromAccountNumber.compareTo(toAccountNumber) < 0;

    Account first = lockAccount(
        fromFirst ? fromAccountNumber : toAccountNumber,
        fromFirst ? fromAccountDigit : toAccountDigit
    );

    Account second = lockAccount(
        fromFirst ? toAccountNumber : fromAccountNumber,
        fromFirst ? toAccountDigit : fromAccountDigit
    );

    return fromFirst
        ? new LockedAccounts(first, second)
        : new LockedAccounts(second, first);
  }

  private void validateAccountHasNoBalance(Account account) {
    if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
      throw new AccountHasBalanceException(ApiErrorMessages.ACCOUNT_HAS_BALANCE);
    }
  }

  private void validateActiveAccount(Account account) {
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new AccountIsNotActiveException(ApiErrorMessages.ACCOUNT_IS_NOT_ACTIVE);
    }
  }

  private String getCurrentUsername() {
    return currentUserService.getUsername();
  }
}
