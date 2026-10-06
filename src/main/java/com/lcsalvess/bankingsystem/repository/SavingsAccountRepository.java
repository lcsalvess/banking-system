package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {
    boolean existsByClientIdAndStatus(Long clientId, AccountStatus status);
}
