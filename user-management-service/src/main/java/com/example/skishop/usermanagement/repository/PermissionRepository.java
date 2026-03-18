package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
}
