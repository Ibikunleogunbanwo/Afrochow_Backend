package com.afrochow.category.service;

import com.afrochow.category.dto.CategoryRequestDto;
import com.afrochow.category.dto.CategoryResponseDto;
import com.afrochow.category.dto.CategoryUpdateRequestDto;
import com.afrochow.category.model.Category;
import com.afrochow.category.repository.CategoryRepository;
import com.afrochow.product.model.Product;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private CategoryService categoryService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder().categoryId(1L).name("African Kitchen")
                .description("Traditional dishes").displayOrder(1).isActive(true).build();
    }

    // ========== read methods ==========

    @Test
    void getAllActiveCategories_returnsOnlyActiveOrderedByDisplayOrder() {
        when(categoryRepository.findByIsActiveOrderByDisplayOrderAsc(true)).thenReturn(List.of(category));

        List<CategoryResponseDto> result = categoryService.getAllActiveCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("African Kitchen");
    }

    @Test
    void getCategoryById_found_returnsDto() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CategoryResponseDto result = categoryService.getCategoryById(1L);

        assertThat(result.getCategoryId()).isEqualTo(1L);
    }

    @Test
    void getCategoryById_notFound_throwsEntityNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void searchCategories_delegatesToRepository() {
        when(categoryRepository.findByNameContainingIgnoreCaseAndIsActive("kitchen", true))
                .thenReturn(List.of(category));

        List<CategoryResponseDto> result = categoryService.searchCategories("kitchen");

        assertThat(result).hasSize(1);
    }

    @Test
    void getCategoryByName_found_returnsDto() {
        when(categoryRepository.findByName("African Kitchen")).thenReturn(Optional.of(category));

        CategoryResponseDto result = categoryService.getCategoryByName("African Kitchen");

        assertThat(result.getName()).isEqualTo("African Kitchen");
    }

    @Test
    void getCategoryByName_notFound_throwsEntityNotFound() {
        when(categoryRepository.findByName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryByName("Missing"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Missing");
    }

    @Test
    void getAllCategories_returnsEveryCategoryRegardlessOfActiveStatus() {
        Category inactive = Category.builder().categoryId(2L).name("Discontinued").isActive(false).build();
        when(categoryRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(category, inactive));

        List<CategoryResponseDto> result = categoryService.getAllCategories();

        assertThat(result).hasSize(2);
    }

    // ========== createCategory ==========

    @Test
    void createCategory_uniqueName_savesAndReturnsDto() {
        CategoryRequestDto request = CategoryRequestDto.builder().name("New Category")
                .description("desc").displayOrder(2).isActive(true).build();
        when(categoryRepository.existsByNameIgnoreCase("New Category")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponseDto result = categoryService.createCategory(request);

        assertThat(result.getName()).isEqualTo("New Category");
    }

    @Test
    void createCategory_duplicateName_throwsIllegalState() {
        CategoryRequestDto request = CategoryRequestDto.builder().name("African Kitchen").build();
        when(categoryRepository.existsByNameIgnoreCase("African Kitchen")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        verify(categoryRepository, never()).save(any());
    }

    // ========== updateCategory ==========

    @Test
    void updateCategory_newUniqueName_updatesAndSaves() {
        CategoryUpdateRequestDto request = CategoryUpdateRequestDto.builder().name("Renamed Kitchen").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCase("Renamed Kitchen")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponseDto result = categoryService.updateCategory(1L, request);

        assertThat(result.getName()).isEqualTo("Renamed Kitchen");
    }

    @Test
    void updateCategory_sameNameDifferentCase_skipsDuplicateCheck() {
        // Renaming to a case-variant of its OWN current name shouldn't trigger the
        // "already exists" guard — updateCategory only checks when the name actually changes.
        CategoryUpdateRequestDto request = CategoryUpdateRequestDto.builder().name("AFRICAN KITCHEN").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.updateCategory(1L, request);

        verify(categoryRepository, never()).existsByNameIgnoreCase(any());
    }

    @Test
    void updateCategory_nameConflictsWithAnotherCategory_throwsIllegalState() {
        CategoryUpdateRequestDto request = CategoryUpdateRequestDto.builder().name("Soups").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCase("Soups")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_notFound_throwsEntityNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(99L,
                CategoryUpdateRequestDto.builder().name("x").build()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateCategory_partialUpdate_onlyChangesProvidedFields() {
        CategoryUpdateRequestDto request = CategoryUpdateRequestDto.builder().description("New description").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponseDto result = categoryService.updateCategory(1L, request);

        assertThat(result.getName()).isEqualTo("African Kitchen"); // unchanged
        assertThat(result.getDescription()).isEqualTo("New description");
    }

    // ========== deleteCategory ==========

    @Test
    void deleteCategory_noProducts_deletes() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategory_hasProducts_throwsIllegalState() {
        Product product = Product.builder().productId(1L).available(true).build();
        category.setProducts(List.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("existing products");
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategory_notFound_throwsEntityNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== activate / deactivate ==========

    @Test
    void activateCategory_setsIsActiveTrue() {
        category.setIsActive(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponseDto result = categoryService.activateCategory(1L);

        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void deactivateCategory_setsIsActiveFalse() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponseDto result = categoryService.deactivateCategory(1L);

        assertThat(result.getIsActive()).isFalse();
    }

    // ========== updateDisplayOrder ==========

    @Test
    void updateDisplayOrder_setsNewOrder() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponseDto result = categoryService.updateDisplayOrder(1L, 5);

        assertThat(result.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    void updateDisplayOrder_notFound_throwsEntityNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateDisplayOrder(99L, 5))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== statistics ==========

    @Test
    void countCategories_delegatesToRepositoryCount() {
        when(categoryRepository.count()).thenReturn(7L);

        assertThat(categoryService.countCategories()).isEqualTo(7L);
    }

    @Test
    void countActiveCategories_delegatesToRepository() {
        when(categoryRepository.countByIsActive(true)).thenReturn(5L);

        assertThat(categoryService.countActiveCategories()).isEqualTo(5L);
    }
}
