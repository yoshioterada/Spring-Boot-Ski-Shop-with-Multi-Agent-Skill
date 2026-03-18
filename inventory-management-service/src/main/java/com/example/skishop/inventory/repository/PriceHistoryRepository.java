package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 価格履歴リポジトリ。
 */
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByProductIdOrderByEffectiveFromDesc(Long productId);
}
