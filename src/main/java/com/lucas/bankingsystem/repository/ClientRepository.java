package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    boolean existsByCpf(String cpf);
}
