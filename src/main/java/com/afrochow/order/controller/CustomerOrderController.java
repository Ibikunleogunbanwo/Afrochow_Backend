package com.afrochow.order.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.order.dto.OrderRequestDto;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
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

    private Long getUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("User authentication details are missing");
        }
        return ((CustomUserDetails) userDetails).getUserId();
    }

    @PostMapping
    @Operation(summary = "Place order", description = "Create a new order")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order successfully created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - User does not have CUSTOMER role"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor or products not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(
            @Valid @RequestBody OrderRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        OrderResponseDto order = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Order placed successfully", order));
    }

    @GetMapping
    @Operation(summary = "Get my orders", description = "Get all orders for the authenticated customer")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getCustomerOrders(userId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active orders", description = "Get all active orders for the authenticated customer")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved active orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getMyActiveOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getCustomerActiveOrders(userId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{publicOrderId}")
    @Operation(summary = "Get order details", description = "Get details of a specific order")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved order details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Order does not belong to customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        OrderResponseDto order = orderService.getCustomerOrder(userId, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PutMapping("/{publicOrderId}/cancel")
    @Operation(summary = "Cancel order", description = "Cancel an order (only if pending or confirmed)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order successfully cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID or order cannot be cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Order does not belong to customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> cancelOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        OrderResponseDto order = orderService.cancelCustomerOrder(userId, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Get order count", description = "Get total number of orders for the customer")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved order count"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<OrderCountStats>> getOrderCount(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        Long orderCount = orderService.countCustomerOrders(userId);
        return ResponseEntity.ok(ApiResponse.success(new OrderCountStats(orderCount)));
    }

    public record OrderCountStats(Long totalOrders) {}
}
