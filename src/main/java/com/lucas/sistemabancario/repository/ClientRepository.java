package com.lucas.sistemabancario.repository;

import com.lucas.sistemabancario.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    boolean existsByCpf(String cpf);
}
