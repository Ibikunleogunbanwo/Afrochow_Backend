package com.afrochow.order.controller;

import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Get all orders
     */
    @GetMapping
    @Operation(summary = "Get all orders", description = "Get all orders in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved all orders"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role")
    })
    public ResponseEntity<List<OrderSummaryResponseDto>> getAllOrders() {
        List<OrderSummaryResponseDto> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * Get active orders
     */
    @GetMapping("/active")
    @Operation(summary = "Get active orders", description = "Get all active orders in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved active orders"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<OrderSummaryResponseDto>> getActiveOrders() {
        List<OrderSummaryResponseDto> orders = orderService.getActiveOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * Get orders by status
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Get orders by status", description = "Get orders filtered by status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved orders by status"),
            @ApiResponse(responseCode = "400", description = "Invalid status parameter"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<OrderSummaryResponseDto>> getOrdersByStatus(
            @PathVariable OrderStatus status
    ) {
        List<OrderSummaryResponseDto> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get specific order details
     */
    @GetMapping("/{publicOrderId}")
    @Operation(summary = "Get order details", description = "Get details of any order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved order details"),
            @ApiResponse(responseCode = "400", description = "Invalid order ID"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponseDto> getOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId
    ) {
        OrderResponseDto order = orderService.getOrderById(publicOrderId);
        return ResponseEntity.ok(order);
    }
}