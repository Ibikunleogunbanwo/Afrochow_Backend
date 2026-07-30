package com.afrochow.order.controller;

import com.afrochow.common.enums.OrderStatus;
import com.afrochow.order.dto.OrderRequestDto;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.order.service.OrderService;
import com.afrochow.orderline.dto.OrderLineRequestDto;
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
 * Controller-layer test for CustomerOrderController.
 *
 * Endpoints take {@code @AuthenticationPrincipal UserDetails} (the interface,
 * not the concrete {@code CustomUserDetails}) and cast internally — the
 * {@code CustomUserDetails} instance {@code authenticatedAsPrincipal}
 * installs still satisfies that resolution since it implements
 * {@code UserDetails}. Class-level {@code @PreAuthorize("hasRole('CUSTOMER')")}
 * is not exercised in this slice (see ControllerSliceTest javadoc).
 */
@ControllerSliceTest(CustomerOrderController.class)
class CustomerOrderControllerTest extends AbstractControllerTest {

    @MockitoBean private OrderService orderService;

    private static final Long USER_ID = 3L;

    private User customerUser() {
        return User.builder().userId(USER_ID).publicUserId("customer-1").build();
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

    private OrderRequestDto validRequest() {
        return OrderRequestDto.builder()
                .vendorPublicId("vendor-1")
                .fulfillmentType("DELIVERY")
                .deliveryAddressPublicId("addr-1")
                .paymentMethodId("pm_test123")
                .orderLines(List.of(OrderLineRequestDto.builder()
                        .productPublicId("prod-1")
                        .quantity(2)
                        .build()))
                .build();
    }

    @Test
    void createOrder_valid_returns201() throws Exception {
        when(orderService.createOrder(eq(USER_ID), any(OrderRequestDto.class)))
                .thenReturn(sampleOrder("order-1", OrderStatus.PENDING));

        mockMvc.perform(post("/customer/orders")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publicOrderId").value("order-1"));
    }

    @Test
    void createOrder_missingFulfillmentType_returns400WithValidationErrors() throws Exception {
        OrderRequestDto request = validRequest();
        request.setFulfillmentType(null);

        mockMvc.perform(post("/customer/orders")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void createOrder_emptyOrderLines_returns400() throws Exception {
        OrderRequestDto request = validRequest();
        request.setOrderLines(List.of());

        mockMvc.perform(post("/customer/orders")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyOrders_returns200() throws Exception {
        when(orderService.getCustomerOrders(USER_ID)).thenReturn(List.of(sampleSummary("order-1")));

        mockMvc.perform(get("/customer/orders")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicOrderId").value("order-1"));
    }

    @Test
    void getMyActiveOrders_returns200() throws Exception {
        when(orderService.getCustomerActiveOrders(USER_ID)).thenReturn(List.of(sampleSummary("order-1")));

        mockMvc.perform(get("/customer/orders/active")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getOrder_returns200() throws Exception {
        when(orderService.getCustomerOrder(USER_ID, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.CONFIRMED));

        mockMvc.perform(get("/customer/orders/{publicOrderId}", "order-1")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void cancelOrder_returns200() throws Exception {
        when(orderService.cancelCustomerOrder(USER_ID, "order-1"))
                .thenReturn(sampleOrder("order-1", OrderStatus.CANCELLED));

        mockMvc.perform(put("/customer/orders/{publicOrderId}/cancel", "order-1")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void getOrderCount_returns200() throws Exception {
        when(orderService.countCustomerOrders(USER_ID)).thenReturn(12L);

        mockMvc.perform(get("/customer/orders/stats/count")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(12));
    }
}
