package com.afrochow.order.controller;

import com.afrochow.common.enums.OrderStatus;
import com.afrochow.order.dto.MarkDeliveredRequestDto;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.order.dto.VendorCancelFulfillmentRequestDto;
import com.afrochow.order.service.OrderService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for VendorOrderController.
 *
 * Every endpoint keys off {@code userDetails.getUsername()} via a plain
 * {@code @AuthenticationPrincipal UserDetails} parameter — resolved through
 * {@code SecurityContextHolder}, so this needs {@code authenticatedAsPrincipal}
 * rather than {@code authenticatedAs} (which only sets the mock request's
 * principal, not the SecurityContext). Class-level
 * {@code @PreAuthorize("hasRole('VENDOR')")} is not exercised in this slice
 * (see ControllerSliceTest javadoc). Covers each of the 14 endpoints once
 * plus a couple of validation cases.
 */
@ControllerSliceTest(VendorOrderController.class)
class VendorOrderControllerTest extends AbstractControllerTest {

    @MockitoBean private OrderService orderService;

    private static final String USERNAME = "vendor-user";

    private User vendorUser() {
        return User.builder().username(USERNAME).publicUserId("vendor-1").build();
    }

    private OrderResponseDto sampleOrder(String publicOrderId, OrderStatus status) {
        return OrderResponseDto.builder()
                .publicOrderId(publicOrderId)
                .status(status)
                .totalAmount(new BigDecimal("25.50"))
                .build();
    }

    private OrderSummaryResponseDto sampleSummary(String publicOrderId) {
        return OrderSummaryResponseDto.builder()
                .publicOrderId(publicOrderId)
                .totalAmount(new BigDecimal("25.50"))
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    void getMyOrders_returns200() throws Exception {
        when(orderService.getVendorOrders(USERNAME)).thenReturn(List.of(sampleSummary("order-1")));

        mockMvc.perform(get("/vendor/orders").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicOrderId").value("order-1"));
    }

    @Test
    void getActiveOrders_returns200() throws Exception {
        when(orderService.getVendorActiveOrders(USERNAME)).thenReturn(List.of(sampleSummary("order-1")));

        mockMvc.perform(get("/vendor/orders/active").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getTodayOrders_returns200() throws Exception {
        when(orderService.getVendorTodayOrders(USERNAME)).thenReturn(List.of(sampleSummary("order-1")));

        mockMvc.perform(get("/vendor/orders/today").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getOrdersByStatus_returns200() throws Exception {
        when(orderService.getVendorOrdersByStatus(USERNAME, OrderStatus.PREPARING))
                .thenReturn(List.of(sampleSummary("order-1")));

        mockMvc.perform(get("/vendor/orders/status/{status}", "PREPARING")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getOrdersByStatus_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/vendor/orders/status/{status}", "NOT_A_STATUS")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_returns200() throws Exception {
        when(orderService.getVendorOrder(USERNAME, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.CONFIRMED));

        mockMvc.perform(get("/vendor/orders/{publicOrderId}", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void acceptOrder_returns200() throws Exception {
        when(orderService.acceptOrder(USERNAME, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.CONFIRMED));

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/accept", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void rejectOrder_returns200() throws Exception {
        when(orderService.rejectOrder(USERNAME, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.CANCELLED));

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/reject", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void unableToFulfil_valid_returns200() throws Exception {
        when(orderService.vendorUnableToFulfil(USERNAME, "order-1", "Out of stock"))
                .thenReturn(sampleOrder("order-1", OrderStatus.CANCELLED));

        VendorCancelFulfillmentRequestDto request = new VendorCancelFulfillmentRequestDto();
        request.setReason("Out of stock");

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/unable-to-fulfil", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void unableToFulfil_missingReason_returns400WithValidationErrors() throws Exception {
        VendorCancelFulfillmentRequestDto request = new VendorCancelFulfillmentRequestDto();

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/unable-to-fulfil", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).vendorUnableToFulfil(any(), any(), any());
    }

    @Test
    void startPreparing_returns200() throws Exception {
        when(orderService.startPreparingOrder(USERNAME, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.PREPARING));

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/preparing", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREPARING"));
    }

    @Test
    void markReady_returns200() throws Exception {
        when(orderService.markOrderReady(USERNAME, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.READY_FOR_PICKUP));

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/ready", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_PICKUP"));
    }

    @Test
    void markOutForDelivery_returns200() throws Exception {
        when(orderService.markOrderOutForDelivery(USERNAME, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.OUT_FOR_DELIVERY));

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/out-for-delivery", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void markDelivered_noBody_returns200() throws Exception {
        when(orderService.markOrderDelivered(USERNAME, "order-1", null))
                .thenReturn(sampleOrder("order-1", OrderStatus.DELIVERED));

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/delivered", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    void markDelivered_withFinalAmount_returns200() throws Exception {
        when(orderService.markOrderDelivered(USERNAME, "order-1", new BigDecimal("20.00")))
                .thenReturn(sampleOrder("order-1", OrderStatus.DELIVERED));

        MarkDeliveredRequestDto request = MarkDeliveredRequestDto.builder()
                .finalAmount(new BigDecimal("20.00"))
                .build();

        mockMvc.perform(put("/vendor/orders/{publicOrderId}/delivered", "order-1")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    void getRevenueStats_returns200() throws Exception {
        when(orderService.getVendorRevenue(USERNAME)).thenReturn(new BigDecimal("10000.00"));
        when(orderService.getVendorTodayRevenue(USERNAME)).thenReturn(new BigDecimal("500.00"));

        mockMvc.perform(get("/vendor/orders/stats/revenue").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRevenue").value(10000.00))
                .andExpect(jsonPath("$.data.todayRevenue").value(500.00));
    }

    @Test
    void getOrderCount_returns200() throws Exception {
        when(orderService.countVendorOrders(USERNAME)).thenReturn(42L);

        mockMvc.perform(get("/vendor/orders/stats/count").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(42));
    }
}
