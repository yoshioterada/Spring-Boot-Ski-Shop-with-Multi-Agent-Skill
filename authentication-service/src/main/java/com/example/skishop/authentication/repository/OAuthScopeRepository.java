package com.example.skishop.authentication.repository;

import com.example.skishop.authentication.entity.OAuthScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OAuthScopeRepository extends JpaRepository<OAuthScope, Long> {
    List<OAuthScope> findByIsDefaultTrue();
    boolean existsByName(String name);
}
