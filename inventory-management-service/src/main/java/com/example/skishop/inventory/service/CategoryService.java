package com.example.skishop.inventory.service;

import com.example.skishop.inventory.dto.request.CreateCategoryRequest;
import com.example.skishop.inventory.dto.request.UpdateCategoryRequest;
import com.example.skishop.inventory.dto.response.CategoryResponse;
import com.example.skishop.inventory.model.Category;
import com.example.skishop.inventory.repository.CategoryRepository;
import com.example.skishop.shared.exception.ConflictException;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * カテゴリサービス。カテゴリの CRUD 機能を提供する。
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * アクティブなカテゴリ一覧を取得する。
     */
    public List<CategoryResponse> findAll() {
        return categoryRepository.findByIsActiveTrue().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * カテゴリを ID で取得する。
     */
    public CategoryResponse findById(Long id) {
        return categoryRepository.findByIdWithChildren(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    /**
     * カテゴリを登録する（管理者のみ）。
     */
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        validateCategoryNameUniqueness(request.name(), request.parentId(), null);

        var category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        category.setIsActive(true);

        if (request.parentId() != null) {
            var parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.parentId()));
            category.setParent(parent);
            category.setLevel(parent.getLevel() + 1);
            category.setPath(parent.getPath() + parent.getId() + "/");
        } else {
            category.setLevel(0);
            category.setPath("/");
        }

        var saved = categoryRepository.save(category);
        log.info("カテゴリを登録しました: categoryId={}, name={}", saved.getId(), saved.getName());
        return CategoryResponse.from(saved);
    }

    /**
     * カテゴリを更新する（管理者のみ）。
     */
    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (request.name() != null) {
            validateCategoryNameUniqueness(request.name(),
                    category.getParent() != null ? category.getParent().getId() : null, id);
            category.setName(request.name());
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.imageUrl() != null) {
            category.setImageUrl(request.imageUrl());
        }
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }

        var saved = categoryRepository.save(category);
        log.info("カテゴリを更新しました: categoryId={}", saved.getId());
        return CategoryResponse.from(saved);
    }

    private void validateCategoryNameUniqueness(String name, Long parentId, Long excludeId) {
        boolean exists;
        if (parentId == null) {
            exists = categoryRepository.existsByNameAndParentIsNull(name);
        } else {
            exists = categoryRepository.existsByNameAndParentId(name, parentId);
        }
        if (exists) {
            throw new ConflictException("DUPLICATE_CATEGORY_NAME",
                    "同一階層に同名のカテゴリが既に存在します: " + name);
        }
    }
}
