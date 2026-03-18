package com.example.skishop.inventory.service;

import com.example.skishop.inventory.dto.request.CreateCategoryRequest;
import com.example.skishop.inventory.dto.request.UpdateCategoryRequest;
import com.example.skishop.inventory.model.Category;
import com.example.skishop.inventory.repository.CategoryRepository;
import com.example.skishop.shared.exception.ConflictException;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @InjectMocks
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    private Category sampleCategory;
    private Category childCategory;

    @BeforeEach
    void setUp() {
        sampleCategory = new Category();
        sampleCategory.setId(1L);
        sampleCategory.setName("スキー用品");
        sampleCategory.setIsActive(true);
        sampleCategory.setLevel(0);
        sampleCategory.setPath("/");

        childCategory = new Category();
        childCategory.setId(2L);
        childCategory.setName("スキー板");
        childCategory.setParent(sampleCategory);
        childCategory.setIsActive(true);
        childCategory.setLevel(1);
        childCategory.setPath("/1/");
    }

    @Test
    @DisplayName("アクティブなカテゴリ一覧を取得した場合、全アクティブカテゴリを返す")
    void should_returnActiveCategories_when_findAllCalled() {
        // Arrange
        when(categoryRepository.findByIsActiveTrue()).thenReturn(List.of(sampleCategory, childCategory));

        // Act
        var result = categoryService.findAll();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("スキー用品");
    }

    @Test
    @DisplayName("有効な ID が指定された場合、カテゴリを返す")
    void should_returnCategory_when_validIdProvided() {
        // Arrange
        when(categoryRepository.findByIdWithChildren(1L)).thenReturn(Optional.of(sampleCategory));

        // Act
        var result = categoryService.findById(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("スキー用品");
    }

    @Test
    @DisplayName("存在しない ID が指定された場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_categoryNotFound() {
        // Arrange
        when(categoryRepository.findByIdWithChildren(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoryService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("有効なリクエストでルートカテゴリを登録した場合、カテゴリを返す")
    void should_createRootCategory_when_validRequestWithoutParentProvided() {
        // Arrange
        var request = new CreateCategoryRequest("新カテゴリ", "説明", null, null);
        when(categoryRepository.existsByNameAndParentIsNull("新カテゴリ")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            var c = (Category) inv.getArgument(0);
            c.setId(3L);
            return c;
        });

        // Act
        var result = categoryService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("新カテゴリ");
        assertThat(result.level()).isEqualTo(0);
        assertThat(result.path()).isEqualTo("/");
    }

    @Test
    @DisplayName("有効なリクエストで子カテゴリを登録した場合、正しい level と path が設定される")
    void should_createChildCategory_when_validRequestWithParentProvided() {
        // Arrange
        var request = new CreateCategoryRequest("子カテゴリ", "説明", 1L, null);
        when(categoryRepository.existsByNameAndParentId("子カテゴリ", 1L)).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            var c = (Category) inv.getArgument(0);
            c.setId(4L);
            return c;
        });

        // Act
        var result = categoryService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.level()).isEqualTo(1);
        assertThat(result.path()).isEqualTo("/1/");
    }

    @Test
    @DisplayName("重複したカテゴリ名で登録した場合、ConflictException をスローする")
    void should_throwConflictException_when_duplicateCategoryNameProvided() {
        // Arrange
        var request = new CreateCategoryRequest("スキー用品", "説明", null, null);
        when(categoryRepository.existsByNameAndParentIsNull("スキー用品")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("スキー用品");
    }

    @Test
    @DisplayName("存在しない親カテゴリ ID で登録した場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_parentCategoryNotFound() {
        // Arrange
        var request = new CreateCategoryRequest("子カテゴリ", "説明", 999L, null);
        when(categoryRepository.existsByNameAndParentId("子カテゴリ", 999L)).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("有効な更新リクエストでカテゴリを更新した場合、更新されたカテゴリを返す")
    void should_returnUpdatedCategory_when_validUpdateRequestProvided() {
        // Arrange
        var request = new UpdateCategoryRequest("更新カテゴリ名", null, null, null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.existsByNameAndParentIsNull("更新カテゴリ名")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(sampleCategory);

        // Act
        var result = categoryService.update(1L, request);

        // Assert
        assertThat(result).isNotNull();
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("存在しない ID でカテゴリ更新した場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_updatingNonExistentCategory() {
        // Arrange
        var request = new UpdateCategoryRequest("更新カテゴリ名", null, null, null);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoryService.update(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }
}
