package com.example.skishop.inventory.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.inventory.dto.CategoryResponse;
import com.example.skishop.inventory.dto.CreateCategoryRequest;
import com.example.skishop.inventory.dto.UpdateCategoryRequest;
import com.example.skishop.inventory.model.Category;
import com.example.skishop.inventory.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final EventPublisher eventPublisher;

    public CategoryService(CategoryRepository categoryRepository,
                           EventPublisher eventPublisher) {
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @CacheEvict(value = {"activeCategories", "categoryById"}, allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Creating category: {}", request.name());

        if (categoryRepository.findByName(request.name()).isPresent()) {
            throw new BusinessRuleViolationException("CAT_DUPLICATE",
                    "カテゴリ名は既に使用されています: " + request.name());
        }

        var category = new Category(request.name(), request.description());
        category.setImageUrl(request.imageUrl());
        category.setSortOrder(request.sortOrder());

        if (request.parentId() != null) {
            categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.parentId()));
            category.setParentId(request.parentId());
        }

        category = categoryRepository.save(category);

        if (request.parentId() != null) {
            addChildToParent(request.parentId(), category.getId());
        }

        log.info("Category created: id={}", category.getId());
        return toResponse(category);
    }

    @Cacheable(value = "categoryById", key = "#categoryId")
    public CategoryResponse getCategoryById(String categoryId) {
        return toResponse(findCategoryOrThrow(categoryId));
    }

    public Page<CategoryResponse> listCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(this::toResponse);
    }

    @Cacheable("activeCategories")
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CategoryResponse> getChildCategories(String parentId) {
        findCategoryOrThrow(parentId);
        return categoryRepository.findByParentId(parentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @CacheEvict(value = {"activeCategories", "categoryById"}, allEntries = true)
    public CategoryResponse updateCategory(String categoryId, UpdateCategoryRequest request) {
        Category category = findCategoryOrThrow(categoryId);

        category.setName(request.name());
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.imageUrl() != null) {
            category.setImageUrl(request.imageUrl());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }

        category = categoryRepository.save(category);
        log.info("Category updated: id={}", category.getId());
        return toResponse(category);
    }

    @CacheEvict(value = {"activeCategories", "categoryById"}, allEntries = true)
    public void deleteCategory(String categoryId) {
        Category category = findCategoryOrThrow(categoryId);

        if (!category.getChildIds().isEmpty()) {
            throw new BusinessRuleViolationException("CAT_HAS_CHILDREN",
                    "子カテゴリが存在するため削除できません");
        }

        if (category.getParentId() != null) {
            removeChildFromParent(category.getParentId(), categoryId);
        }

        categoryRepository.delete(category);
        log.info("Category deleted: id={}", categoryId);
    }

    private Category findCategoryOrThrow(String categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private void addChildToParent(String parentId, String childId) {
        categoryRepository.findById(parentId).ifPresent(parent -> {
            parent.getChildIds().add(childId);
            categoryRepository.save(parent);
        });
    }

    private void removeChildFromParent(String parentId, String childId) {
        categoryRepository.findById(parentId).ifPresent(parent -> {
            parent.getChildIds().remove(childId);
            categoryRepository.save(parent);
        });
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(), c.getName(), c.getDescription(), c.getParentId(),
                c.getChildIds(), c.getImageUrl(), c.getSortOrder(), c.isActive(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
