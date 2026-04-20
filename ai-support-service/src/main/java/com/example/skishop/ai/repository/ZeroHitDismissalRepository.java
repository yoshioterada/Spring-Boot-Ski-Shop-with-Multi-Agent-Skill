package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.ZeroHitDismissal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ZeroHitDismissalRepository extends MongoRepository<ZeroHitDismissal, String> {

    Optional<ZeroHitDismissal> findByNormalizedKeyword(String normalizedKeyword);

    List<ZeroHitDismissal> findAll();
}
