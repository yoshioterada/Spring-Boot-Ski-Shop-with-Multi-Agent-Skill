package com.example.skishop.inventory.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.inventory.dto.CategoryResponse;
import com.example.skishop.inventory.dto.CreateCategoryRequest;
import com.example.skishop.inventory.dto.UpdateCategoryRequest;
import com.example.skishop.inventory.model.Category;
import com.example.skishop.inventory.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private EventPublisher eventPublisher;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, eventPublisher);
    }

    private Category createCategory(String id, String name) {
        Category category = new Category(name, "Description for " + name);
        setId(category, id);
        return category;
    }

    private void setId(Category category, String id) {
        try {
            Field idField = Category.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("createCategory - カテゴリ作成")
    class CreateCategoryTests {

        @Test
        @DisplayName("有効なリクエストでカテゴリ作成が成功する")
        void should_createCategory_when_validRequest() {
            // Arrange
            var request = new CreateCategoryRequest("Skis", "Alpine skis", null, null, 1);
            var saved = new Category("Skis", "Alpine skis");
            setId(saved, "cat-1");
            when(categoryRepository.findByName("Skis")).thenReturn(Optional.empty());
            when(categoryRepository.save(any(Category.class))).thenReturn(saved);

            // Act
            CategoryResponse response = categoryService.createCategory(request);

            // Assert
            assertThat(response.name()).isEqualTo("Skis");
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("重複カテゴリ名で作成が失敗する")
        void should_throwException_when_duplicateName() {
            // Arrange
            var request = new CreateCategoryRequest("Skis", "desc", null, null, 0);
            when(categoryRepository.findByName("Skis")).thenReturn(Optional.of(new Category("Skis", "desc")));

            // Act & Assert
            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Skis");
        }

        @Test
        @DisplayName("存在しない親カテゴリ指定で失敗する")
        void should_throwException_when_parentNotExists() {
            // Arrange
            var request = new CreateCategoryRequest("Child", "desc", "nonexistent", null, 0);
            when(categoryRepository.findByName("Child")).thenReturn(Optional.empty());
            when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getCategoryById - カテゴリ取得")
    class GetCategoryTests {

        @Test
        @DisplayName("存在するIDでカテゴリ取得が成功する")
        void should_returnCategory_when_exists() {
            // Arrange
            Category category = createCategory("cat-1", "Skis");
            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // Act
            CategoryResponse response = categoryService.getCategoryById("cat-1");

            // Assert
            assertThat(response.name()).isEqualTo("Skis");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_notExists() {
            // Arrange
            when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> categoryService.getCategoryById("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateCategory - カテゴリ更新")
    class UpdateCategoryTests {

        @Test
        @DisplayName("カテゴリ更新が成功する")
        void should_updateCategory_when_validRequest() {
            // Arrange
            Category category = createCategory("cat-1", "Skis");
            var request = new UpdateCategoryRequest("Updated Skis", "New desc", null, 5, null);
            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));
            when(categoryRepository.save(any(Category.class))).thenReturn(category);

            // Act
            CategoryResponse response = categoryService.updateCategory("cat-1", request);

            // Assert
            assertThat(response).isNotNull();
            verify(categoryRepository).save(any(Category.class));
        }
    }

    @Nested
    @DisplayName("deleteCategory - カテゴリ削除")
    class DeleteCategoryTests {

        @Test
        @DisplayName("子カテゴリがない場合削除が成功する")
        void should_deleteCategory_when_noChildren() {
            // Arrange
            Category category = createCategory("cat-1", "Skis");
            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // Act
            categoryService.deleteCategory("cat-1");

            // Assert
            verify(categoryRepository).delete(category);
        }

        @Test
        @DisplayName("子カテゴリがある場合削除が失敗する")
        void should_throwException_when_hasChildren() {
            // Arrange
            Category category = createCategory("cat-1", "Skis");
            category.setChildIds(List.of("child-1"));
            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // Act & Assert
            assertThatThrownBy(() -> categoryService.deleteCategory("cat-1"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("子カテゴリ");
        }
    }

    @Nested
    @DisplayName("listCategories - カテゴリ一覧")
    class ListCategoryTests {

        @Test
        @DisplayName("カテゴリ一覧のページネーション取得が成功する")
        void should_returnPagedCategories() {
            // Arrange
            Category category = createCategory("cat-1", "Skis");
            Page<Category> page = new PageImpl<>(List.of(category));
            when(categoryRepository.findAll(any(PageRequest.class))).thenReturn(page);

            // Act
            Page<CategoryResponse> result = categoryService.listCategories(PageRequest.of(0, 20));

            // Assert
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("アクティブなカテゴリのみ取得する")
        void should_returnActiveCategories() {
            // Arrange
            Category category = createCategory("cat-1", "Skis");
            when(categoryRepository.findByActiveTrue()).thenReturn(List.of(category));

            // Act
            List<CategoryResponse> result = categoryService.getActiveCategories();

            // Assert
            assertThat(result).hasSize(1);
        }
    }
}
