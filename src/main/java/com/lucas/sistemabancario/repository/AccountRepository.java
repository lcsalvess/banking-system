package com.lucas.sistemabancario.repository;

import com.lucas.sistemabancario.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {
    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    Long getNextAccountNumber();
}
