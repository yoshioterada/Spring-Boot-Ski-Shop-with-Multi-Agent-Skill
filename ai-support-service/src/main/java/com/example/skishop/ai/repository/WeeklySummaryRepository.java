package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.WeeklySummary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * F3 週次サマリー MongoDB リポジトリ.
 */
public interface WeeklySummaryRepository extends MongoRepository<WeeklySummary, String> {

    Optional<WeeklySummary> findByWeekStartDate(LocalDate weekStartDate);
}
