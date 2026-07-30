package com.afrochow.favorite.controller;

import com.afrochow.common.enums.FavoriteType;
import com.afrochow.common.exceptions.DuplicateResourceException;
import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.favorite.dto.FavoriteRequestDto;
import com.afrochow.favorite.dto.FavoriteResponseDto;
import com.afrochow.favorite.service.FavoriteService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for FavoriteController.
 *
 * See CategoryControllerTest / AbstractControllerTest / ControllerSliceTest
 * for the shared @WebMvcTest slice setup. All customer-scoped endpoints here
 * take a plain {@code Authentication} parameter and call {@code getName()}
 * on it (resolved to the username FavoriteService looks up by) — that's
 * satisfied via {@link #authenticatedAs}, not spring-security-test, since
 * the security filter chain is disabled in this slice anyway.
 * @PreAuthorize("hasRole('CUSTOMER')") is likewise not exercised here (see
 * ControllerSliceTest javadoc).
 */
@ControllerSliceTest(FavoriteController.class)
class FavoriteControllerTest extends AbstractControllerTest {

    @MockitoBean private FavoriteService favoriteService;

    private static final String USERNAME = "alice";

    private FavoriteResponseDto sampleVendorFavorite(String publicFavoriteId, String vendorPublicId) {
        return FavoriteResponseDto.builder()
                .publicFavoriteId(publicFavoriteId)
                .favoriteType(FavoriteType.VENDOR)
                .vendor(FavoriteResponseDto.VendorBasicInfo.builder()
                        .publicVendorId(vendorPublicId)
                        .restaurantName("Mama's Kitchen")
                        .isActive(true)
                        .build())
                .build();
    }

    private FavoriteResponseDto sampleProductFavorite(String publicFavoriteId, String productPublicId) {
        return FavoriteResponseDto.builder()
                .publicFavoriteId(publicFavoriteId)
                .favoriteType(FavoriteType.PRODUCT)
                .product(FavoriteResponseDto.ProductBasicInfo.builder()
                        .publicProductId(productPublicId)
                        .productName("Jollof Rice")
                        .isAvailable(true)
                        .build())
                .build();
    }

    // ========== ADD FAVORITE ==========

    @Test
    void addFavorite_validVendor_returns201() throws Exception {
        FavoriteRequestDto request = new FavoriteRequestDto(FavoriteType.VENDOR, "vendor-1", null);
        when(favoriteService.addFavorite(eq(USERNAME), any(FavoriteRequestDto.class)))
                .thenReturn(sampleVendorFavorite("fav-1", "vendor-1"));

        mockMvc.perform(post("/favorites")
                        .with(authenticatedAs(USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vendor.publicVendorId").value("vendor-1"));
    }

    @Test
    void addFavorite_missingFavoriteType_returns400WithValidationErrors() throws Exception {
        FavoriteRequestDto request = new FavoriteRequestDto(null, "vendor-1", null);

        mockMvc.perform(post("/favorites")
                        .with(authenticatedAs(USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].field").value("favoriteType"));

        verify(favoriteService, never()).addFavorite(any(), any());
    }

    @Test
    void addFavorite_alreadyFavorited_returns409() throws Exception {
        FavoriteRequestDto request = new FavoriteRequestDto(FavoriteType.VENDOR, "vendor-1", null);
        when(favoriteService.addFavorite(eq(USERNAME), any(FavoriteRequestDto.class)))
                .thenThrow(new DuplicateResourceException("Vendor is already in favorites"));

        mockMvc.perform(post("/favorites")
                        .with(authenticatedAs(USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vendor is already in favorites"));
    }

    // ========== REMOVE FAVORITE ==========

    @Test
    void removeFavorite_valid_returns200() throws Exception {
        FavoriteRequestDto request = new FavoriteRequestDto(FavoriteType.PRODUCT, null, "product-1");
        doNothing().when(favoriteService).removeFavorite(eq(USERNAME), any(FavoriteRequestDto.class));

        mockMvc.perform(delete("/favorites")
                        .with(authenticatedAs(USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void removeFavorite_notInFavorites_returns404() throws Exception {
        FavoriteRequestDto request = new FavoriteRequestDto(FavoriteType.PRODUCT, null, "product-1");
        doThrow(new ResourceNotFoundException("Product is not in favorites"))
                .when(favoriteService).removeFavorite(eq(USERNAME), any(FavoriteRequestDto.class));

        mockMvc.perform(delete("/favorites")
                        .with(authenticatedAs(USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product is not in favorites"));
    }

    // ========== LIST / QUERY ==========

    @Test
    void getAllFavorites_returns200WithPage() throws Exception {
        Page<FavoriteResponseDto> page = new PageImpl<>(
                List.of(sampleVendorFavorite("fav-1", "vendor-1")), PageRequest.of(0, 20), 1);
        when(favoriteService.getAllFavorites(eq(USERNAME), any())).thenReturn(page);

        mockMvc.perform(get("/favorites").with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].publicFavoriteId").value("fav-1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getFavoritesByType_returns200WithFilteredPage() throws Exception {
        Page<FavoriteResponseDto> page = new PageImpl<>(
                List.of(sampleProductFavorite("fav-2", "product-1")), PageRequest.of(0, 20), 1);
        when(favoriteService.getFavoritesByType(eq(USERNAME), eq(FavoriteType.PRODUCT), any()))
                .thenReturn(page);

        mockMvc.perform(get("/favorites/type/{favoriteType}", "PRODUCT")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].product.publicProductId").value("product-1"));
    }

    @Test
    void isVendorFavorited_returns200True() throws Exception {
        when(favoriteService.isVendorFavorited(USERNAME, "vendor-1")).thenReturn(true);

        mockMvc.perform(get("/favorites/vendor/{vendorPublicId}/is-favorited", "vendor-1")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void isProductFavorited_returns200False() throws Exception {
        when(favoriteService.isProductFavorited(USERNAME, "product-1")).thenReturn(false);

        mockMvc.perform(get("/favorites/product/{productPublicId}/is-favorited", "product-1")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void getMyFavoriteCount_returns200() throws Exception {
        when(favoriteService.getCustomerFavoriteCount(USERNAME)).thenReturn(7L);

        mockMvc.perform(get("/favorites/my-count").with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7));
    }

    // ========== PUBLIC COUNT ENDPOINTS (no auth needed) ==========

    @Test
    void getVendorFavoriteCount_public_returns200() throws Exception {
        when(favoriteService.getVendorFavoriteCount("vendor-1")).thenReturn(42L);

        mockMvc.perform(get("/favorites/vendor/{vendorPublicId}/count", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    void getProductFavoriteCount_public_returns200() throws Exception {
        when(favoriteService.getProductFavoriteCount("product-1")).thenReturn(3L);

        mockMvc.perform(get("/favorites/product/{productPublicId}/count", "product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
    }
}
