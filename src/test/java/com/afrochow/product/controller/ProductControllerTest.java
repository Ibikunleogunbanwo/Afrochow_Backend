package com.afrochow.product.controller;

import com.afrochow.product.dto.ProductRequestDto;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.dto.ProductSummaryResponseDto;
import com.afrochow.product.dto.ProductUpdateRequestDto;
import com.afrochow.product.service.ProductService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for ProductController.
 *
 * Vendor endpoints mix {@code userDetails.getUsername()} (createProduct,
 * getMyProducts, updateProduct, deleteProduct) and {@code getUserId()}
 * (getMyProduct, toggleAvailability, uploadProductImage) — so the {@link User}
 * passed to {@code authenticatedAsPrincipal} needs both populated.
 */
@ControllerSliceTest(ProductController.class)
class ProductControllerTest extends AbstractControllerTest {

    @MockitoBean private ProductService productService;

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "vendor-user";

    private User vendorUser() {
        return User.builder()
                .userId(USER_ID)
                .username(USERNAME)
                .publicUserId("vendor-1")
                .build();
    }

    private ProductResponseDto sampleProduct(String publicProductId) {
        return ProductResponseDto.builder()
                .publicProductId(publicProductId)
                .name("Jollof Rice")
                .price(new BigDecimal("15.99"))
                .available(true)
                .categoryId(1L)
                .build();
    }

    private ProductSummaryResponseDto sampleSummary(String publicProductId) {
        return ProductSummaryResponseDto.builder()
                .publicProductId(publicProductId)
                .name("Jollof Rice")
                .price(new BigDecimal("15.99"))
                .available(true)
                .categoryId(1L)
                .build();
    }

    private ProductRequestDto validRequest() {
        return ProductRequestDto.builder()
                .name("Jollof Rice")
                .price(new BigDecimal("15.99"))
                .preparationTimeMinutes(30)
                .categoryId(1L)
                .build();
    }

    // ========== PUBLIC ENDPOINTS ==========

    @Test
    void getProduct_returns200() throws Exception {
        when(productService.getProductByPublicId("prod-1")).thenReturn(sampleProduct("prod-1"));

        mockMvc.perform(get("/products/{publicProductId}", "prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Jollof Rice"));
    }

    @Test
    void getVendorProducts_returns200() throws Exception {
        when(productService.getProductsByVendor("vendor-1", true))
                .thenReturn(List.of(sampleSummary("prod-1")));

        mockMvc.perform(get("/products/vendor/{publicVendorId}", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicProductId").value("prod-1"));
    }

    @Test
    void getProductsByCategory_returns200() throws Exception {
        when(productService.getProductsByCategory(1L, true))
                .thenReturn(List.of(sampleSummary("prod-1")));

        mockMvc.perform(get("/products/category/{categoryId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ========== VENDOR ENDPOINTS ==========

    @Test
    void createProduct_valid_returns201() throws Exception {
        when(productService.createProduct(eq(USERNAME), any(ProductRequestDto.class)))
                .thenReturn(sampleProduct("prod-1"));

        mockMvc.perform(post("/vendor/products")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publicProductId").value("prod-1"));
    }

    @Test
    void createProduct_missingName_returns400WithValidationErrors() throws Exception {
        ProductRequestDto request = validRequest();
        request.setName(null);

        mockMvc.perform(post("/vendor/products")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(productService, never()).createProduct(any(), any());
    }

    @Test
    void getMyProducts_returns200() throws Exception {
        when(productService.getVendorProducts(USERNAME)).thenReturn(List.of(sampleProduct("prod-1")));

        mockMvc.perform(get("/vendor/products")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicProductId").value("prod-1"));
    }

    @Test
    void getMyProduct_returns200() throws Exception {
        when(productService.getVendorProduct(USER_ID, "prod-1")).thenReturn(sampleProduct("prod-1"));

        mockMvc.perform(get("/vendor/products/{publicProductId}", "prod-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Jollof Rice"));
    }

    @Test
    void updateProduct_returns200() throws Exception {
        when(productService.updateProduct(eq(USERNAME), eq("prod-1"), any(ProductUpdateRequestDto.class)))
                .thenReturn(sampleProduct("prod-1"));

        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("Jollof Rice Deluxe")
                .build();

        mockMvc.perform(put("/vendor/products/{publicProductId}", "prod-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteProduct_returns200() throws Exception {
        doNothing().when(productService).deleteProduct(USERNAME, "prod-1");

        mockMvc.perform(delete("/vendor/products/{publicProductId}", "prod-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void toggleAvailability_returns200() throws Exception {
        ProductResponseDto toggled = sampleProduct("prod-1");
        toggled.setAvailable(false);
        when(productService.toggleProductAvailability(USER_ID, "prod-1")).thenReturn(toggled);

        mockMvc.perform(patch("/vendor/products/{publicProductId}/availability", "prod-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void uploadProductImage_returns200() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "product.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(productService.uploadProductImage(eq(USER_ID), eq("prod-1"), any()))
                .thenReturn(sampleProduct("prod-1"));

        mockMvc.perform(multipart("/vendor/products/{publicProductId}/image", "prod-1")
                        .file(image)
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
