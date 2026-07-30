package com.afrochow.stats.controller;

import com.afrochow.stats.dto.PlatformStatsDto;
import com.afrochow.stats.service.StatsService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for StatsController — a single public,
 * unauthenticated endpoint.
 */
@ControllerSliceTest(StatsController.class)
class StatsControllerTest extends AbstractControllerTest {

    @MockitoBean private StatsService statsService;

    @Test
    void getPlatformStats_returns200() throws Exception {
        PlatformStatsDto stats = PlatformStatsDto.builder()
                .totalVendors(100L)
                .totalActiveVendors(80L)
                .totalVerifiedVendors(70L)
                .totalCustomers(5000L)
                .totalActiveCustomers(3000L)
                .totalProducts(2000L)
                .totalAvailableProducts(1800L)
                .totalOrders(15000L)
                .totalCompletedOrders(14000L)
                .averageDeliveryTimeMinutes(35)
                .totalReviews(4000L)
                .averagePlatformRating(4.3)
                .build();
        when(statsService.getPlatformStats()).thenReturn(stats);

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVendors").value(100))
                .andExpect(jsonPath("$.data.averagePlatformRating").value(4.3));
    }
}
