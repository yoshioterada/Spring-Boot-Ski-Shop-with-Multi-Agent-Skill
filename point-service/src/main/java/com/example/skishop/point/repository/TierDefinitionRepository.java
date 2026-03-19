package com.example.skishop.point.repository;

import com.example.skishop.point.model.TierDefinition;
import com.example.skishop.point.model.TierDefinition.TierLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TierDefinitionRepository extends JpaRepository<TierDefinition, UUID> {

    Optional<TierDefinition> findByLevel(TierLevel level);
}
