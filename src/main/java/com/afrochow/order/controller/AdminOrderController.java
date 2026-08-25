package com.afrochow.order.controller;

import com.afrochow.common.response.ApiResponse;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderStatsDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for admin order management
 * Endpoints (requires ADMIN role):
 * - GET /admin/orders - Get all orders
 * - GET /admin/orders/active - Get active orders
 * - GET /admin/orders/status/{status} - Get orders by status
 * - GET /admin/orders/{publicOrderId} - Get order details
 */
@RestController
@RequestMapping("/admin/orders")
@Tag(name = "Admin Orders", description = "Admin order management endpoints")
// ORDERS area = OPERATIONS or CUSTOMER_SUPPORT department (or SUPERADMIN).
@PreAuthorize("@deptAccess.can('ORDERS')")
@Validated
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Get all orders (paginated)", description = "Get orders in the system, newest first")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllOrders(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("orderTime").descending());
        Page<OrderSummaryResponseDto> orderPage = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(toPagedBody(orderPage)));
    }

    private Map<String, Object> toPagedBody(Page<OrderSummaryResponseDto> orderPage) {
        return Map.of(
                "content",       orderPage.getContent(),
                "totalElements", orderPage.getTotalElements(),
                "totalPages",    orderPage.getTotalPages(),
                "page",          orderPage.getNumber(),
                "size",          orderPage.getSize()
        );
    }

    @GetMapping("/stats")
    @Operation(summary = "Get order stats", description = "Aggregate order counts by status, for the dashboard stat cards")
    public ResponseEntity<ApiResponse<OrderStatsDto>> getOrderStats() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderStats()));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active orders", description = "Get all active orders in the system")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved active orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getActiveOrders() {
        List<OrderSummaryResponseDto> orders = orderService.getActiveOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get orders by status (paginated)", description = "Get orders filtered by status, newest first")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved orders by status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrdersByStatus(
            @PathVariable OrderStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<OrderSummaryResponseDto> orderPage = orderService.getOrdersByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPagedBody(orderPage)));
    }

    @GetMapping("/{publicOrderId}")
    @Operation(summary = "Get order details", description = "Get details of any order")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved order details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId
    ) {
        OrderResponseDto order = orderService.getOrderById(publicOrderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/{publicOrderId}/cancel")
    @Operation(summary = "Cancel order (admin)", description = "Admin override to cancel any non-terminal order")
    public ResponseEntity<ApiResponse<OrderResponseDto>> cancelOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId
    ) {
        OrderResponseDto order = orderService.adminCancelOrder(publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }
}
