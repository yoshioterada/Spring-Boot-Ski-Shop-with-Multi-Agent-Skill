package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.Role;
import com.example.skishop.usermanagement.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
