package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Provides persistence operations and specialized queries for bank accounts.
 */
public interface AccountRepository extends
    JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

  /**
   * Retrieves all accounts with their associated clients using pagination.
   *
   * @param pageable pagination and sorting configuration
   * @return a page of accounts with their clients loaded
   */
  @EntityGraph(attributePaths = "client")
  @Query(
      """
      SELECT a
      FROM Account a
      """
  )
  Page<Account> findAllWithClient(Pageable pageable);

  /**
   * Finds an account by its account number.
   *
   * @param accountNumber account number to search for
   * @return the matching account, if found
   */
  Optional<Account> findByAccountNumber(String accountNumber);

  /**
   * Acquires a pessimistic write lock on an account row and returns its identifier.
   *
   * <p>Must be called within an active transaction.
   *
   * @param accountNumber account number identifying the row to lock
   * @return the identifier of the locked account, if found
   */
  @Query(
      value =
          """
          SELECT id
          FROM public.accounts
          WHERE account_number = :accountNumber
          FOR UPDATE
          """,
      nativeQuery = true
  )
  Optional<Long> lockIdByAccountNumber(
      @Param("accountNumber") String accountNumber
  );

  /**
   * Finds an account by its account number, eagerly loading its associated client.
   *
   * @param accountNumber account number to search for
   * @return the matching account with its client loaded, if found
   */
  @Query("SELECT a FROM Account a JOIN FETCH a.client WHERE a.accountNumber = :accountNumber")
  Optional<Account> findByAccountNumberWithClient(
      @Param("accountNumber") String accountNumber
  );

  /**
   * Retrieves the next account number from the database sequence.
   *
   * @return the next sequence value
   */
  @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
  Long getNextAccountNumber();

  /**
   * Retrieves active or otherwise specified savings accounts after a given identifier.
   *
   * <p>Results are ordered by account identifier to support stable batch processing.
   *
   * @param status account status used to filter the results
   * @param afterId identifier after which accounts are retrieved
   * @param pageable pagination configuration, including the batch size
   * @return savings accounts matching the status and identifier criteria
   */
  @Query(
      """
      SELECT s
      FROM SavingsAccount s
      WHERE s.status = :status
        AND s.id > :afterId
      ORDER BY s.id ASC
      """
  )
  List<SavingsAccount> findNextSavingsAccountsByStatus(
      @Param("status") AccountStatus status,
      @Param("afterId") Long afterId,
      Pageable pageable
  );
}
