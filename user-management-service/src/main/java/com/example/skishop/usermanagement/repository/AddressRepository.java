package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserId(UUID userId);
    Optional<Address> findByIdAndUserId(Long id, UUID userId);
}
