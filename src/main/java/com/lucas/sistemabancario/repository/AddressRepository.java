package com.lucas.sistemabancario.repository;

import com.lucas.sistemabancario.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}
