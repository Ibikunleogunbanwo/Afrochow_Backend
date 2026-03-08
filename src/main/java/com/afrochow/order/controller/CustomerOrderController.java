package com.afrochow.order.controller;

import com.afrochow.order.dto.OrderRequestDto;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for customer order management
 * Endpoints (requires CUSTOMER role):
 * - POST /customer/orders - Place new order
 * - GET /customer/orders - Get all my orders
 * - GET /customer/orders/active - Get my active orders
 * - GET /customer/orders/{publicOrderId} - Get order details
 * - PUT /customer/orders/{publicOrderId}/cancel - Cancel order
 * - GET /customer/orders/stats/count - Get order count
 */
@RestController
@RequestMapping("/customer/orders")
@Tag(name = "Customer Orders", description = "Customer order management endpoints")
@PreAuthorize("hasRole('CUSTOMER')")
@Validated
public class CustomerOrderController {

    private final OrderService orderService;

    public CustomerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Extract user ID from UserDetails with validation
     */
    private Long getUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("User authentication details are missing");
        } else {
            userDetails.getUsername();
        }
        try {
            return Long.parseLong(userDetails.getUsername());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid user ID format", e);
        }
    }

    /**
     * Place a new order
     */
    @PostMapping
    @Operation(summary = "Place order", description = "Create a new order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User does not have CUSTOMER role"),
            @ApiResponse(responseCode = "404", description = "Vendor or products not found")
    })
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody OrderRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        OrderResponseDto order = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * Get all orders for authenticated customer
     */
    @GetMapping
    @Operation(summary = "Get my orders", description = "Get all orders for the authenticated customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved orders"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<OrderSummaryResponseDto>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getCustomerOrders(userId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get active orders for authenticated customer
     */
    @GetMapping("/active")
    @Operation(summary = "Get active orders", description = "Get all active orders for the authenticated customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved active orders"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<OrderSummaryResponseDto>> getMyActiveOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getCustomerActiveOrders(userId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get specific order details
     */
    @GetMapping("/{publicOrderId}")
    @Operation(summary = "Get order details", description = "Get details of a specific order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved order details"),
            @ApiResponse(responseCode = "400", description = "Invalid order ID"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Order does not belong to customer"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponseDto> getOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        OrderResponseDto order = orderService.getCustomerOrder(userId, publicOrderId);
        return ResponseEntity.ok(order);
    }

    /**
     * Cancel an order
     */
    @PutMapping("/{publicOrderId}/cancel")
    @Operation(summary = "Cancel order", description = "Cancel an order (only if pending or confirmed)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order successfully cancelled"),
            @ApiResponse(responseCode = "400", description = "Invalid order ID or order cannot be cancelled"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Order does not belong to customer"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponseDto> cancelOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        OrderResponseDto order = orderService.cancelCustomerOrder(userId, publicOrderId);
        return ResponseEntity.ok(order);
    }

    /**
     * Get order count statistics
     */
    @GetMapping("/stats/count")
    @Operation(summary = "Get order count", description = "Get total number of orders for the customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved order count"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<OrderCountStats> getOrderCount(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        Long orderCount = orderService.countCustomerOrders(userId);

        OrderCountStats stats = new OrderCountStats(orderCount);
        return ResponseEntity.ok(stats);
    }

    /**
     * Inner class for order count statistics
     */
    public record OrderCountStats(Long totalOrders) {}
}