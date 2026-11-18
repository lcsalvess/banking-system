package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.client.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence operations and queries for clients.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

  /**
   * Checks whether a client with the specified CPF already exists.
   *
   * @param cpf the CPF to check
   * @return {@code true} if a client with the CPF exists; {@code false} otherwise
   */
  boolean existsByCpf(String cpf);

  /**
   * Retrieves clients whose names contain the specified text,
   * ignoring case differences.
   *
   * @param name the text to search for in client names
   * @param pageable the pagination and sorting configuration
   * @return a page containing the matching clients
   */
  Page<Client> findByNameContainingIgnoreCase(
      String name,
      Pageable pageable
  );
}
