package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.SavingsAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {
    boolean existsByClientId(Long clientId);
}
