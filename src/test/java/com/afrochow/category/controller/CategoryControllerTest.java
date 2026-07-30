package com.afrochow.category.controller;

import com.afrochow.category.dto.CategoryRequestDto;
import com.afrochow.category.dto.CategoryResponseDto;
import com.afrochow.category.dto.CategoryUpdateRequestDto;
import com.afrochow.category.service.CategoryService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for CategoryController.
 *
 * Uses @ControllerSliceTest (a @WebMvcTest slice with the JWT filter excluded
 * and the security filter chain disabled — see AbstractControllerTest /
 * ControllerSliceTest for why). This deliberately does NOT exercise
 * @PreAuthorize/@deptAccess role enforcement on the /admin/categories endpoints
 * (that requires the full SecurityConfig + method-security aspect weaving,
 * which @WebMvcTest does not load). Scope here is: request routing, @Valid
 * DTO validation, correct service invocation, and HTTP status / response-shape
 * correctness via GlobalExceptionHandler.
 */
@ControllerSliceTest(CategoryController.class)
class CategoryControllerTest extends AbstractControllerTest {

    @MockitoBean private CategoryService categoryService;

    private CategoryResponseDto sampleCategory(Long id, String name) {
        return CategoryResponseDto.builder()
                .categoryId(id)
                .name(name)
                .description("desc")
                .displayOrder(0)
                .isActive(true)
                .productCount(0)
                .activeProductCount(0)
                .build();
    }

    // ========== PUBLIC ENDPOINTS ==========

    @Test
    void getStoreCategories_returnsLabelList() throws Exception {
        mockMvc.perform(get("/categories/store-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getAllCategories_returnsActiveCategories() throws Exception {
        when(categoryService.getAllActiveCategories())
                .thenReturn(List.of(sampleCategory(1L, "Groceries")));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Groceries"));
    }

    @Test
    void getCategory_found_returns200WithCategory() throws Exception {
        when(categoryService.getCategoryById(1L)).thenReturn(sampleCategory(1L, "Groceries"));

        mockMvc.perform(get("/categories/{categoryId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(1))
                .andExpect(jsonPath("$.data.name").value("Groceries"));
    }

    @Test
    void getCategory_notFound_returns404() throws Exception {
        when(categoryService.getCategoryById(99L))
                .thenThrow(new EntityNotFoundException("Category not found"));

        mockMvc.perform(get("/categories/{categoryId}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void searchCategories_returnsMatches() throws Exception {
        when(categoryService.searchCategories("gro"))
                .thenReturn(List.of(sampleCategory(1L, "Groceries")));

        mockMvc.perform(get("/categories/search").param("query", "gro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Groceries"));
    }

    @Test
    void searchCategories_missingQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/categories/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing required parameter: query"));
    }

    @Test
    void getCategoryByName_found_returns200() throws Exception {
        when(categoryService.getCategoryByName("Groceries")).thenReturn(sampleCategory(1L, "Groceries"));

        mockMvc.perform(get("/categories/name/{name}", "Groceries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Groceries"));
    }

    @Test
    void getCategoryByName_notFound_returns404() throws Exception {
        when(categoryService.getCategoryByName("Ghost"))
                .thenThrow(new EntityNotFoundException("Category with name 'Ghost' not found"));

        mockMvc.perform(get("/categories/name/{name}", "Ghost"))
                .andExpect(status().isNotFound());
    }

    // ========== ADMIN ENDPOINTS (security not exercised in this slice) ==========

    @Test
    void getAllCategoriesAdmin_returns200() throws Exception {
        when(categoryService.getAllCategories())
                .thenReturn(List.of(sampleCategory(1L, "Groceries"), sampleCategory(2L, "Produce")));

        mockMvc.perform(get("/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void createCategory_valid_returns201() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder().name("Bakery").build();
        when(categoryService.createCategory(any(CategoryRequestDto.class)))
                .thenReturn(sampleCategory(3L, "Bakery"));

        mockMvc.perform(post("/admin/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Bakery"));
    }

    @Test
    void createCategory_blankName_returns400WithValidationErrors() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder().name("").build();

        mockMvc.perform(post("/admin/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].field").value("name"));

        verify(categoryService, never()).createCategory(any());
    }

    @Test
    void createCategory_duplicateName_returns400() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder().name("Groceries").build();
        when(categoryService.createCategory(any(CategoryRequestDto.class)))
                .thenThrow(new IllegalStateException("Category with name 'Groceries' already exists"));

        mockMvc.perform(post("/admin/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category with name 'Groceries' already exists"));
    }

    @Test
    void updateCategory_valid_returns200() throws Exception {
        CategoryUpdateRequestDto request = CategoryUpdateRequestDto.builder().name("Updated").build();
        when(categoryService.updateCategory(eq(1L), any(CategoryUpdateRequestDto.class)))
                .thenReturn(sampleCategory(1L, "Updated"));

        mockMvc.perform(put("/admin/categories/{categoryId}", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"));
    }

    @Test
    void updateCategory_notFound_returns404() throws Exception {
        CategoryUpdateRequestDto request = CategoryUpdateRequestDto.builder().name("Updated").build();
        when(categoryService.updateCategory(eq(99L), any(CategoryUpdateRequestDto.class)))
                .thenThrow(new EntityNotFoundException("Category not found"));

        mockMvc.perform(put("/admin/categories/{categoryId}", 99L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCategory_duplicateName_returns400() throws Exception {
        CategoryUpdateRequestDto request = CategoryUpdateRequestDto.builder().name("Produce").build();
        when(categoryService.updateCategory(eq(1L), any(CategoryUpdateRequestDto.class)))
                .thenThrow(new IllegalStateException("Category with name 'Produce' already exists"));

        mockMvc.perform(put("/admin/categories/{categoryId}", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteCategory_success_returns200() throws Exception {
        doNothing().when(categoryService).deleteCategory(1L);

        mockMvc.perform(delete("/admin/categories/{categoryId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteCategory_hasProducts_returns400() throws Exception {
        doThrow(new IllegalStateException("Cannot delete category with existing products. " +
                "Please remove or reassign all products first."))
                .when(categoryService).deleteCategory(1L);

        mockMvc.perform(delete("/admin/categories/{categoryId}", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Cannot delete category with existing products. Please remove or reassign all products first."));
    }

    @Test
    void deleteCategory_notFound_returns404() throws Exception {
        doThrow(new EntityNotFoundException("Category not found"))
                .when(categoryService).deleteCategory(99L);

        mockMvc.perform(delete("/admin/categories/{categoryId}", 99L))
                .andExpect(status().isNotFound());
    }

    @Test
    void activateCategory_returns200() throws Exception {
        when(categoryService.activateCategory(1L)).thenReturn(sampleCategory(1L, "Groceries"));

        mockMvc.perform(patch("/admin/categories/{categoryId}/activate", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Groceries"));
    }

    @Test
    void deactivateCategory_returns200() throws Exception {
        when(categoryService.deactivateCategory(1L)).thenReturn(sampleCategory(1L, "Groceries"));

        mockMvc.perform(patch("/admin/categories/{categoryId}/deactivate", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Groceries"));
    }

    @Test
    void updateDisplayOrder_valid_returns200() throws Exception {
        when(categoryService.updateDisplayOrder(1L, 5)).thenReturn(sampleCategory(1L, "Groceries"));

        mockMvc.perform(patch("/admin/categories/{categoryId}/display-order", 1L)
                        .param("order", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(categoryService).updateDisplayOrder(1L, 5);
    }

    @Test
    void updateDisplayOrder_missingParam_returns400() throws Exception {
        mockMvc.perform(patch("/admin/categories/{categoryId}/display-order", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: order"));
    }
}
