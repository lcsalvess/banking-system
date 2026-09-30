package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.CheckingAccount;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckingAccountRepository extends JpaRepository<CheckingAccount, Long> {
    boolean existsByClientIdAndStatus(Long clientId, AccountStatus status);
}
