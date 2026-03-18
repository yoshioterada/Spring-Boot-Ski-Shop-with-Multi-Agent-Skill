package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * カテゴリリポジトリ。
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByIsActiveTrue();

    List<Category> findByParentIsNullAndIsActiveTrue();

    List<Category> findByParentIdAndIsActiveTrue(Long parentId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.id = :id")
    Optional<Category> findByIdWithChildren(@Param("id") Long id);

    boolean existsByNameAndParentId(String name, Long parentId);

    boolean existsByNameAndParentIsNull(String name);
}
