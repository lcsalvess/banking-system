package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckingAccountRepository extends JpaRepository<CheckingAccount, Long> {
    boolean existsByClientIdAndStatus(Long clientId, AccountStatus status);
}
