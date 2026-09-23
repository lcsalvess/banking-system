package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.CheckingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckingAccountRepository extends JpaRepository<CheckingAccount, Long> {
    boolean existsByClientId(Long clientId);
}
