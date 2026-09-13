package com.lucas.sistemabancario.repository;

import com.lucas.sistemabancario.entity.SavingsAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {
    boolean existsByClientId(Long clientId);
}
