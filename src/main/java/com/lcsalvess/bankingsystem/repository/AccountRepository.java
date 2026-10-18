package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = "client")
    @Query("""
    SELECT a
    FROM Account a
    """)
    Page<Account> findAllWithClient(Pageable pageable);

    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT a
       FROM Account a
       WHERE a.accountNumber = :accountNumber
       """)
    Optional<Account> findByAccountNumberForUpdate(
            @Param("accountNumber") String accountNumber
    );

    @Query("SELECT a FROM Account a JOIN FETCH a.client WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithClient(
            @Param("accountNumber") String accountNumber
    );

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    Long getNextAccountNumber();

    @Query("""
    SELECT s
    FROM SavingsAccount s
    WHERE s.status = :status
      AND s.id > :afterId
    ORDER BY s.id ASC
    """)
    List<SavingsAccount> findNextSavingsAccountsByStatus(
            @Param("status") AccountStatus status,
            @Param("afterId") Long afterId,
            Pageable pageable
    );
}
