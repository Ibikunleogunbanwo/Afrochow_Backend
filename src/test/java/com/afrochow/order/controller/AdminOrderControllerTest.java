package com.afrochow.order.controller;

import com.afrochow.common.enums.OrderStatus;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderStatsDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.order.service.OrderService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for AdminOrderController.
 *
 * None of these endpoints take an authentication parameter — access control
 * is entirely via class-level {@code @PreAuthorize("@deptAccess.can('ORDERS')")},
 * which is not exercised in this slice (see ControllerSliceTest javadoc), so
 * no auth setup is needed in these tests at all.
 */
@ControllerSliceTest(AdminOrderController.class)
class AdminOrderControllerTest extends AbstractControllerTest {

    @MockitoBean private OrderService orderService;

    private OrderSummaryResponseDto sampleSummary(String publicOrderId) {
        return OrderSummaryResponseDto.builder()
                .publicOrderId(publicOrderId)
                .totalAmount(new BigDecimal("25.50"))
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    void getAllOrders_returns200WithPage() throws Exception {
        Page<OrderSummaryResponseDto> page = new PageImpl<>(
                List.of(sampleSummary("order-1")), PageRequest.of(0, 25), 1);
        when(orderService.getAllOrders(any())).thenReturn(page);

        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].publicOrderId").value("order-1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getOrderStats_returns200() throws Exception {
        OrderStatsDto stats = OrderStatsDto.builder()
                .total(100).pending(10).confirmed(20).preparing(15)
                .readyForPickup(5).outForDelivery(5).delivered(40)
                .cancelled(3).refunded(2).active(55)
                .build();
        when(orderService.getOrderStats()).thenReturn(stats);

        mockMvc.perform(get("/admin/orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(100))
                .andExpect(jsonPath("$.data.active").value(55));
    }

    @Test
    void getActiveOrders_returns200() throws Exception {
        when(orderService.getActiveOrders()).thenReturn(List.of(sampleSummary("order-1")));

        mockMvc.perform(get("/admin/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getOrdersByStatus_returns200WithPage() throws Exception {
        Page<OrderSummaryResponseDto> page = new PageImpl<>(
                List.of(sampleSummary("order-1")), PageRequest.of(0, 25), 1);
        when(orderService.getOrdersByStatus(eq(OrderStatus.CONFIRMED), any())).thenReturn(page);

        mockMvc.perform(get("/admin/orders/status/{status}", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].publicOrderId").value("order-1"));
    }

    @Test
    void getOrdersByStatus_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/admin/orders/status/{status}", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_returns200() throws Exception {
        OrderResponseDto order = OrderResponseDto.builder()
                .publicOrderId("order-1")
                .status(OrderStatus.DELIVERED)
                .build();
        when(orderService.getOrderById("order-1")).thenReturn(order);

        mockMvc.perform(get("/admin/orders/{publicOrderId}", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    void cancelOrder_returns200() throws Exception {
        OrderResponseDto order = OrderResponseDto.builder()
                .publicOrderId("order-1")
                .status(OrderStatus.CANCELLED)
                .build();
        when(orderService.adminCancelOrder("order-1")).thenReturn(order);

        mockMvc.perform(post("/admin/orders/{publicOrderId}/cancel", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
