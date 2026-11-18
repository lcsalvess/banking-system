package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.address.Address;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence operations for client addresses.
 */
public interface AddressRepository extends JpaRepository<Address, Long> {
}
