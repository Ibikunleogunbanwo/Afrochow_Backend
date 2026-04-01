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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for vendor order management
 * Endpoints (requires VENDOR role):
 * - GET /vendor/orders - Get all my orders
 * - GET /vendor/orders/active - Get my active orders
 * - GET /vendor/orders/today - Get today's orders
 * - GET /vendor/orders/status/{status} - Get orders by status
 * - GET /vendor/orders/{publicOrderId} - Get order details
 * - PUT /vendor/orders/{publicOrderId}/accept - Accept order
 * - PUT /vendor/orders/{publicOrderId}/reject - Reject order
 * - PUT /vendor/orders/{publicOrderId}/preparing - Start preparing
 * - PUT /vendor/orders/{publicOrderId}/ready - Mark as ready
 * - PUT /vendor/orders/{publicOrderId}/out-for-delivery - Mark out for delivery
 * - PUT /vendor/orders/{publicOrderId}/delivered - Mark as delivered
 * - GET /vendor/orders/stats/revenue - Get revenue statistics
 * - GET /vendor/orders/stats/count - Get order count
 */
@RestController
@RequestMapping("/vendor/orders")
@Tag(name = "Vendor Orders", description = "Vendor order management endpoints")
@PreAuthorize("hasRole('VENDOR')")
@Validated
public class VendorOrderController {

    private final OrderService orderService;

    public VendorOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    private String getUsername(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("User authentication details are missing");
        }
        return userDetails.getUsername();
    }

    @GetMapping
    @Operation(summary = "Get my orders", description = "Get all orders for the authenticated vendor")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing authentication"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - User does not have VENDOR role")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getVendorOrders(username);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active orders", description = "Get all active orders for the authenticated vendor")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved active orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getActiveOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getVendorActiveOrders(username);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/today")
    @Operation(summary = "Get today's orders", description = "Get all orders placed today")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved today's orders"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> getTodayOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getVendorTodayOrders(username);
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
            @PathVariable OrderStatus status,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        List<OrderSummaryResponseDto> orders = orderService.getVendorOrdersByStatus(username, status);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{publicOrderId}")
    @Operation(summary = "Get order details", description = "Get details of a specific order")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved order details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Order does not belong to vendor"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        OrderResponseDto order = orderService.getVendorOrder(username, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PutMapping("/{publicOrderId}/accept")
    @Operation(summary = "Accept order", description = "Accept a pending order")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order successfully accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID or order cannot be accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> acceptOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        OrderResponseDto order = orderService.acceptOrder(username, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order accepted", order));
    }

    @PutMapping("/{publicOrderId}/reject")
    @Operation(summary = "Reject order", description = "Reject a pending order")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order successfully rejected"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID or order cannot be rejected"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> rejectOrder(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        OrderResponseDto order = orderService.rejectOrder(username, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order rejected", order));
    }

    @PutMapping("/{publicOrderId}/preparing")
    @Operation(summary = "Start preparing", description = "Mark order as being prepared")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order marked as preparing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID or invalid state transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> startPreparing(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        OrderResponseDto order = orderService.startPreparingOrder(username, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order is being prepared", order));
    }

    @PutMapping("/{publicOrderId}/ready")
    @Operation(summary = "Mark ready", description = "Mark order as ready for pickup/delivery")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order marked as ready"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID or invalid state transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> markReady(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        OrderResponseDto order = orderService.markOrderReady(username, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as ready", order));
    }

    @PutMapping("/{publicOrderId}/out-for-delivery")
    @Operation(summary = "Out for delivery", description = "Mark order as out for delivery")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order marked as out for delivery"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID or invalid state transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> markOutForDelivery(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        OrderResponseDto order = orderService.markOrderOutForDelivery(username, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order is out for delivery", order));
    }

    @PutMapping("/{publicOrderId}/delivered")
    @Operation(summary = "Mark delivered", description = "Mark order as delivered")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order marked as delivered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order ID or invalid state transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> markDelivered(
            @PathVariable @NotBlank(message = "Order ID cannot be blank") String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        OrderResponseDto order = orderService.markOrderDelivered(username, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as delivered", order));
    }

    @GetMapping("/stats/revenue")
    @Operation(summary = "Get revenue", description = "Get total and today's revenue")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved revenue statistics"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<RevenueStats>> getRevenueStats(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        BigDecimal totalRevenue = orderService.getVendorRevenue(username);
        BigDecimal todayRevenue = orderService.getVendorTodayRevenue(username);
        return ResponseEntity.ok(ApiResponse.success(new RevenueStats(totalRevenue, todayRevenue)));
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Get order count", description = "Get total number of orders for the vendor")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved order count"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<OrderCountStats>> getOrderCount(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = getUsername(userDetails);
        Long orderCount = orderService.countVendorOrders(username);
        return ResponseEntity.ok(ApiResponse.success(new OrderCountStats(orderCount)));
    }

    public record RevenueStats(BigDecimal totalRevenue, BigDecimal todayRevenue) {}

    public record OrderCountStats(Long totalOrders) {}
}
