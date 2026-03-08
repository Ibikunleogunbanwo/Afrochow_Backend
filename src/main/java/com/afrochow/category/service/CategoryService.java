package com.afrochow.category.service;

import com.afrochow.category.dto.CategoryRequestDto;
import com.afrochow.category.dto.CategoryUpdateRequestDto;
import com.afrochow.category.dto.CategoryResponseDto;
import com.afrochow.category.model.Category;
import com.afrochow.category.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing categories
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // ========== PUBLIC METHODS (no authentication required) ==========

    /**
     * Get all active categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getAllActiveCategories() {
        List<Category> categories = categoryRepository.findByIsActiveOrderByDisplayOrderAsc(true);
        return categories.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get category by ID
     */
    @Transactional(readOnly = true)
    public CategoryResponseDto getCategoryById(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
        return toResponseDto(category);
    }

    /**
     * Search categories by name
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> searchCategories(String query) {
        List<Category> categories = categoryRepository.findByNameContainingIgnoreCaseAndIsActive(query, true);
        return categories.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get category by exact name
     */
    @Transactional(readOnly = true)
    public CategoryResponseDto getCategoryByName(String name) {
        Category category = categoryRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Category with name '" + name + "' not found"));
        return toResponseDto(category);
    }

    // ========== ADMIN METHODS (requires ADMIN role with canManageCategories permission) ==========

    /**
     * Get all categories (including inactive)
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getAllCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        return categories.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Create new category (admin only)
     */
    @Transactional
    public CategoryResponseDto createCategory(CategoryRequestDto request) {
        // Check if category name already exists
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalStateException("Category with name '" + request.getName() + "' already exists");
        }

        Category category = toEntity(request);
        Category savedCategory = categoryRepository.save(category);

        return toResponseDto(savedCategory);
    }

    /**
     * Update category (admin only)
     */
    @Transactional
    public CategoryResponseDto updateCategory(Long categoryId, CategoryUpdateRequestDto request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        // Check if new name conflicts with existing category
        if (request.getName() != null && !request.getName().equalsIgnoreCase(category.getName())) {
            if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
                throw new IllegalStateException("Category with name '" + request.getName() + "' already exists");
            }
        }

        updateEntityFromDto(request, category);
        Category updatedCategory = categoryRepository.save(category);

        return toResponseDto(updatedCategory);
    }

    /**
     * Delete category (admin only)
     */
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        // Check if category has products
        if (category.hasProducts()) {
            throw new IllegalStateException("Cannot delete category with existing products. " +
                    "Please remove or reassign all products first.");
        }

        categoryRepository.delete(category);
    }

    /**
     * Activate category (admin only)
     */
    @Transactional
    public CategoryResponseDto activateCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        category.setIsActive(true);
        Category updatedCategory = categoryRepository.save(category);

        return toResponseDto(updatedCategory);
    }

    /**
     * Deactivate category (admin only)
     */
    @Transactional
    public CategoryResponseDto deactivateCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        category.setIsActive(false);
        Category updatedCategory = categoryRepository.save(category);

        return toResponseDto(updatedCategory);
    }

    /**
     * Update display order (admin only)
     */
    @Transactional
    public CategoryResponseDto updateDisplayOrder(Long categoryId, Integer newOrder) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        category.setDisplayOrder(newOrder);
        Category updatedCategory = categoryRepository.save(category);

        return toResponseDto(updatedCategory);
    }

    // ========== STATISTICS ==========

    /**
     * Count all categories
     */
    public Long countCategories() {
        return categoryRepository.count();
    }

    /**
     * Count active categories
     */
    public Long countActiveCategories() {
        return categoryRepository.countByIsActive(true);
    }

    // ========== MAPPING METHODS ==========

    private CategoryResponseDto toResponseDto(Category category) {
        return CategoryResponseDto.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .description(category.getDescription())
                .iconUrl(category.getIconUrl())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .productCount(category.getProductCount())
                .activeProductCount(category.getActiveProductCount())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private Category toEntity(CategoryRequestDto dto) {
        return Category.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .iconUrl(dto.getIconUrl())
                .displayOrder(dto.getDisplayOrder())
                .isActive(dto.getIsActive())
                .build();
    }

    private void updateEntityFromDto(CategoryUpdateRequestDto dto, Category category) {
        if (dto.getName() != null) category.setName(dto.getName());
        if (dto.getDescription() != null) category.setDescription(dto.getDescription());
        if (dto.getIconUrl() != null) category.setIconUrl(dto.getIconUrl());
        if (dto.getDisplayOrder() != null) category.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getIsActive() != null) category.setIsActive(dto.getIsActive());
    }
}