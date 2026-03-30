package com.afrochow.order.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@Validated
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Get all orders", description = "Get all orders in the system")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved all orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getAllOrders() {
        List<OrderSummaryResponseDto> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
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
    @Operation(summary = "Get orders by status", description = "Get orders filtered by status")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved orders by status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getOrdersByStatus(
            @PathVariable OrderStatus status
    ) {
        List<OrderSummaryResponseDto> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(orders));
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
