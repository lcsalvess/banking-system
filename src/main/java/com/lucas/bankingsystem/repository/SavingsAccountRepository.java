package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.SavingsAccount;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {
    boolean existsByClientIdAndStatus(Long clientId, AccountStatus status);
}
