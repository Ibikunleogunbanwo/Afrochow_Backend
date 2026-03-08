package com.afrochow.category.controller;

import com.afrochow.category.dto.CategoryRequestDto;
import com.afrochow.category.dto.CategoryUpdateRequestDto;
import com.afrochow.category.dto.CategoryResponseDto;
import com.afrochow.category.service.CategoryService;
import com.afrochow.common.ApiResponse;
import com.afrochow.common.ResponseBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for category management
 *
 * Public endpoints (no auth required):
 * - GET /categories - Get all active categories
 * - GET /categories/{categoryId} - Get category details
 * - GET /categories/search - Search categories by name
 *
 * Admin endpoints (requires ADMIN role with canManageCategories):
 * - GET /admin/categories - Get all categories (including inactive)
 * - POST /admin/categories - Create new category
 * - PUT /admin/categories/{categoryId} - Update category
 * - DELETE /admin/categories/{categoryId} - Delete category
 * - PATCH /admin/categories/{categoryId}/activate - Activate category
 * - PATCH /admin/categories/{categoryId}/deactivate - Deactivate category
 * - PATCH /admin/categories/{categoryId}/display-order - Update display order
 */
@RestController
@Tag(name = "Categories", description = "Category management endpoints")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // ========== PUBLIC ENDPOINTS ==========

    /**
     * Get all active categories
     */
    @GetMapping("/categories")
    @Operation(summary = "Get categories", description = "Get all active categories ordered by display order")
    public ResponseEntity<ApiResponse<List<CategoryResponseDto>>> getAllCategories() {
        List<CategoryResponseDto> categories = categoryService.getAllActiveCategories();
        return ResponseBuilder.ok("Categories retrieved successfully", categories);
    }

    /**
     * Get category by ID
     */
    @GetMapping("/categories/{categoryId}")
    @Operation(summary = "Get category", description = "Get category details by ID")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> getCategory(@PathVariable Long categoryId) {
        CategoryResponseDto category = categoryService.getCategoryById(categoryId);
        return ResponseBuilder.ok("Category retrieved successfully", category);
    }

    /**
     * Search categories by name
     */
    @GetMapping("/categories/search")
    @Operation(summary = "Search categories", description = "Search active categories by name")
    public ResponseEntity<ApiResponse<List<CategoryResponseDto>>> searchCategories(@RequestParam String query) {
        List<CategoryResponseDto> categories = categoryService.searchCategories(query);
        return ResponseBuilder.ok("Search completed successfully", categories);
    }

    /**
     * Get category by exact name
     */
    @GetMapping("/categories/name/{name}")
    @Operation(summary = "Get category by name", description = "Get category details by exact name")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> getCategoryByName(@PathVariable String name) {
        CategoryResponseDto category = categoryService.getCategoryByName(name);
        return ResponseBuilder.ok("Category retrieved successfully", category);
    }

    // ========== ADMIN ENDPOINTS ==========

    /**
     * Get all categories (including inactive)
     */
    @GetMapping("/admin/categories")
    @Operation(summary = "Get all categories", description = "Get all categories including inactive ones (admin only)")
    public ResponseEntity<ApiResponse<List<CategoryResponseDto>>> getAllCategoriesAdmin() {
        List<CategoryResponseDto> categories = categoryService.getAllCategories();
        return ResponseBuilder.ok("All categories retrieved successfully", categories);
    }

    /**
     * Create new category
     */
    @PostMapping("/admin/categories")
    @Operation(summary = "Create category", description = "Create a new category (admin only)")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> createCategory(@Valid @RequestBody CategoryRequestDto request) {
        CategoryResponseDto category = categoryService.createCategory(request);
        return ResponseBuilder.created("Category created successfully", category);
    }

    /**
     * Update category
     */
    @PutMapping("/admin/categories/{categoryId}")
    @Operation(summary = "Update category", description = "Update an existing category (admin only)")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequestDto request
    ) {
        CategoryResponseDto category = categoryService.updateCategory(categoryId, request);
        return ResponseBuilder.ok("Category updated successfully", category);
    }

    /**
     * Delete category
     */
    @DeleteMapping("/admin/categories/{categoryId}")
    @Operation(summary = "Delete category", description = "Delete a category (admin only, only if no products)")
    public ResponseEntity<ApiResponse<String>> deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseBuilder.ok("Category deleted successfully");
    }

    /**
     * Activate category
     */
    @PatchMapping("/admin/categories/{categoryId}/activate")
    @Operation(summary = "Activate category", description = "Activate a category (admin only)")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> activateCategory(@PathVariable Long categoryId) {
        CategoryResponseDto category = categoryService.activateCategory(categoryId);
        return ResponseBuilder.ok("Category activated successfully", category);
    }

    /**
     * Deactivate category
     */
    @PatchMapping("/admin/categories/{categoryId}/deactivate")
    @Operation(summary = "Deactivate category", description = "Deactivate a category (admin only)")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> deactivateCategory(@PathVariable Long categoryId) {
        CategoryResponseDto category = categoryService.deactivateCategory(categoryId);
        return ResponseBuilder.ok("Category deactivated successfully", category);
    }

    /**
     * Update display order
     */
    @PatchMapping("/admin/categories/{categoryId}/display-order")
    @Operation(summary = "Update display order", description = "Update category display order (admin only)")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> updateDisplayOrder(
            @PathVariable Long categoryId,
            @RequestParam Integer order
    ) {
        CategoryResponseDto category = categoryService.updateDisplayOrder(categoryId, order);
        return ResponseBuilder.ok("Display order updated successfully", category);
    }
}
