package com.afrochow.search;
import com.afrochow.search.controller.SearchController;
import com.afrochow.search.service.SearchService;

import com.afrochow.common.response.ApiResponse;
import com.afrochow.common.enums.ScheduleType;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for SearchController — a fully public, unauthenticated
 * read-only search API backed entirely by SearchService.
 */
@ControllerSliceTest(SearchController.class)
class SearchControllerTest extends AbstractControllerTest {

    @MockitoBean private SearchService searchService;

    private VendorProfileResponseDto sampleVendor() {
        return VendorProfileResponseDto.builder()
                .publicUserId("vendor-1")
                .restaurantName("Mama's Kitchen")
                .build();
    }

    private ProductResponseDto sampleProduct() {
        return ProductResponseDto.builder()
                .publicProductId("prod-1")
                .name("Jollof Rice")
                .price(new BigDecimal("15.99"))
                .build();
    }

    @Test
    void getMarketStatus_returns200() throws Exception {
        when(searchService.isMarketServed("Calgary", null, null)).thenReturn(true);

        mockMvc.perform(get("/search/market-status").param("city", "Calgary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void getVendorByPublicId_returns200() throws Exception {
        when(searchService.getVendorByPublicId("vendor-1", null, null)).thenReturn(sampleVendor());

        mockMvc.perform(get("/search/vendors/{publicUserId}", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantName").value("Mama's Kitchen"));
    }

    @Test
    void getVendorsByProductName_returns200() throws Exception {
        when(searchService.getVendorsByProductName("jollof")).thenReturn(List.of(sampleVendor()));

        mockMvc.perform(get("/search/vendors/by-product").param("query", "jollof"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getVendorsByProductName_missingQuery_returns400() throws Exception {
        mockMvc.perform(get("/search/vendors/by-product"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getVendorsByCity_returns200() throws Exception {
        when(searchService.getVendorsByCity("Calgary")).thenReturn(List.of(sampleVendor()));

        mockMvc.perform(get("/search/vendors/city/{city}", "Calgary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getTopRatedVendors_returns200() throws Exception {
        when(searchService.getTopRatedVendors()).thenReturn(List.of(sampleVendor()));

        mockMvc.perform(get("/search/vendors/top-rated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getVerifiedVendors_returns200() throws Exception {
        when(searchService.getVerifiedVendors()).thenReturn(List.of(sampleVendor()));

        mockMvc.perform(get("/search/vendors/verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void advancedVendorSearch_returns200() throws Exception {
        when(searchService.advancedVendorSearch(eq("jollof"), isNull(), eq("Calgary"), eq(true), isNull()))
                .thenReturn(List.of(sampleVendor()));

        mockMvc.perform(get("/search/vendors/advanced")
                        .param("query", "jollof")
                        .param("city", "Calgary")
                        .param("isVerified", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getFeaturedProducts_returns200() throws Exception {
        when(searchService.getFeaturedProducts(eq("Calgary"), isNull(), isNull(), eq(ScheduleType.SAME_DAY)))
                .thenReturn(List.of(sampleProduct()));

        mockMvc.perform(get("/search/products/featured")
                        .param("city", "Calgary")
                        .param("scheduleType", "SAME_DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getProductsNearMe_returns200() throws Exception {
        when(searchService.getProductsNearMe("Montreal")).thenReturn(List.of(sampleProduct()));

        mockMvc.perform(get("/search/products/near-me")
                        .param("city", "Montreal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getProductsNearCoordinates_returns200() throws Exception {
        when(searchService.getProductsNearCoordinates(45.5017, -73.5673, 25.0))
                .thenReturn(List.of(sampleProduct()));

        mockMvc.perform(get("/search/products/near-coordinates")
                        .param("lat", "45.5017")
                        .param("lng", "-73.5673"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getProductsNearCoordinates_missingRequiredParams_returns400() throws Exception {
        mockMvc.perform(get("/search/products/near-coordinates"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMonthlyPopularProducts_returns200() throws Exception {
        when(searchService.getProductsNearMe("Montreal")).thenReturn(List.of(sampleProduct()));

        mockMvc.perform(get("/search/products/monthly-popular")
                        .param("city", "Montreal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getSimilarProducts_returns200() throws Exception {
        when(searchService.getSimilarProducts("prod-1", "Calgary", null, null)).thenReturn(List.of(sampleProduct()));

        mockMvc.perform(get("/search/products/{publicProductId}/similar", "prod-1")
                        .param("city", "Calgary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getPopularProductNames_returns200() throws Exception {
        when(searchService.getPopularProductNames(5)).thenReturn(List.of("Jollof Rice", "Egusi Soup"));

        mockMvc.perform(get("/search/products/popular/names"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void advancedProductSearch_returns200() throws Exception {
        ApiResponse.PageResponse<ProductResponseDto> page = ApiResponse.PageResponse.<ProductResponseDto>builder()
                .content(List.of(sampleProduct()))
                .pageNumber(0).pageSize(20).totalElements(1L).totalPages(1)
                .first(true).last(true).hasNext(false).hasPrevious(false)
                .build();
        when(searchService.advancedProductSearch(
                eq("jollof"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/search/products/advanced").param("query", "jollof"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].publicProductId").value("prod-1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getVendorsNearCoordinates_returns200() throws Exception {
        when(searchService.getVendorsNearCoordinates(51.05, -114.07, 25.0)).thenReturn(List.of(sampleVendor()));

        mockMvc.perform(get("/search/vendors/near-coordinates")
                        .param("lat", "51.05")
                        .param("lng", "-114.07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getVendorsNearCoordinates_missingRequiredParams_returns400() throws Exception {
        mockMvc.perform(get("/search/vendors/near-coordinates"))
                .andExpect(status().isBadRequest());
    }
}
