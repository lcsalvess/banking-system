package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}
