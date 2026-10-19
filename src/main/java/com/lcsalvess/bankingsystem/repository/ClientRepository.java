package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.client.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    boolean existsByCpf(String cpf);

    Page<Client> findByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );
}
