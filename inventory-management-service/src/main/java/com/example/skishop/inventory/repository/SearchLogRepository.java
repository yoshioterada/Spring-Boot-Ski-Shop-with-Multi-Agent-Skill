package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.SearchLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchLogRepository extends MongoRepository<SearchLog, String> {
}
