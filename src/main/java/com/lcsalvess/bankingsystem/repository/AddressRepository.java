package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.address.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}
