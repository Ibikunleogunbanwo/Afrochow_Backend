package com.afrochow.admin.controller;

import com.afrochow.category.model.Category;
import com.afrochow.favorite.repository.FavoriteRepository;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import com.afrochow.vendor.model.VendorProfile;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for AdminProductController.
 *
 * This one talks to {@code ProductRepository}/{@code FavoriteRepository}
 * directly rather than a service layer, so both need {@code @MockitoBean}.
 * No endpoint takes an authentication parameter — access control is via
 * class-level {@code @PreAuthorize("@deptAccess.can('PRODUCTS')")} (plus a
 * stricter {@code hasRole('SUPERADMIN')} override on delete), neither of
 * which is exercised in this slice (see ControllerSliceTest javadoc).
 */
@ControllerSliceTest(AdminProductController.class)
class AdminProductControllerTest extends AbstractControllerTest {

    @MockitoBean private ProductRepository productRepository;
    @MockitoBean private FavoriteRepository favoriteRepository;

    private VendorProfile sampleVendor() {
        return VendorProfile.builder()
                .user(User.builder().publicUserId("vendor-1").build())
                .restaurantName("Mama's Kitchen")
                .build();
    }

    private Category sampleCategory() {
        return Category.builder().name("Rice Dishes").build();
    }

    private Product sampleProduct() {
        return Product.builder()
                .publicProductId("prod-1")
                .name("Jollof Rice")
                .price(new BigDecimal("15.99"))
                .available(true)
                .adminVisible(true)
                .isFeatured(false)
                .vendor(sampleVendor())
                .category(sampleCategory())
                .build();
    }

    @Test
    void getAllProducts_noFilters_returns200WithPage() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), PageRequest.of(0, 20), 1);
        when(productRepository.findAllForAdmin(any())).thenReturn(page);

        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].publicProductId").value("prod-1"))
                .andExpect(jsonPath("$.data.content[0].vendorName").value("Mama's Kitchen"));
    }

    @Test
    void getAllProducts_withSearch_returns200() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), PageRequest.of(0, 20), 1);
        when(productRepository.searchForAdmin(eq("jollof"), any())).thenReturn(page);

        mockMvc.perform(get("/admin/products").param("search", "jollof"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Jollof Rice"));
    }

    @Test
    void getAllProducts_withFeaturedFilter_returns200() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), PageRequest.of(0, 20), 1);
        when(productRepository.findByIsFeaturedOrderByFeaturedAtDesc(eq(true), any())).thenReturn(page);

        mockMvc.perform(get("/admin/products").param("featured", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void toggleFeature_pinsUnfeaturedProduct_returns200() throws Exception {
        Product product = sampleProduct();
        when(productRepository.findByPublicProductId("prod-1")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/admin/products/{publicProductId}/toggle-feature", "prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFeatured").value(true));
    }

    @Test
    void toggleFeature_notFound_returns404() throws Exception {
        when(productRepository.findByPublicProductId("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(put("/admin/products/{publicProductId}/toggle-feature", "ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAdminFeaturedProducts_returns200() throws Exception {
        Product featured = sampleProduct();
        featured.setIsFeatured(true);
        when(productRepository.findAdminFeaturedProducts()).thenReturn(List.of(featured));

        mockMvc.perform(get("/admin/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicProductId").value("prod-1"));
    }

    @Test
    void clearAllFeatured_returns200() throws Exception {
        Product featured = sampleProduct();
        featured.setIsFeatured(true);
        when(productRepository.findAllFeaturedForAdmin()).thenReturn(List.of(featured));
        when(productRepository.saveAll(anyList())).thenReturn(List.of(featured));

        mockMvc.perform(delete("/admin/products/featured/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(1));
    }

    @Test
    void toggleVisibility_suspendsVisibleProduct_returns200() throws Exception {
        Product product = sampleProduct();
        when(productRepository.findByPublicProductId("prod-1")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/admin/products/{publicProductId}/visibility", "prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminVisible").value(false));
    }

    @Test
    void deleteProduct_returns200() throws Exception {
        Product product = sampleProduct();
        when(productRepository.findByPublicProductId("prod-1")).thenReturn(Optional.of(product));
        doNothing().when(favoriteRepository).deleteAllByProduct(product);
        doNothing().when(productRepository).delete(product);

        mockMvc.perform(delete("/admin/products/{publicProductId}", "prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(favoriteRepository).deleteAllByProduct(product);
        verify(productRepository).delete(product);
    }

    @Test
    void deleteProduct_notFound_returns404() throws Exception {
        when(productRepository.findByPublicProductId("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/admin/products/{publicProductId}", "ghost"))
                .andExpect(status().isNotFound());

        verify(favoriteRepository, never()).deleteAllByProduct(any());
    }
}
