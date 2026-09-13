package com.lucas.sistemabancario.repository;

import com.lucas.sistemabancario.entity.CheckingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckingAccountRepository extends JpaRepository<CheckingAccount, Long> {
    boolean existsByClientId(Long clientId);
}
