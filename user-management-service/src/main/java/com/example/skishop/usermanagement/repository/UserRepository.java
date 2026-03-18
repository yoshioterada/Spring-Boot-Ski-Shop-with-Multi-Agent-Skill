package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.User;
import com.example.skishop.usermanagement.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Page<User> findByStatusAndFirstNameContainingIgnoreCase(UserStatus status, String firstName, Pageable pageable);
    boolean existsByEmail(String email);
}
