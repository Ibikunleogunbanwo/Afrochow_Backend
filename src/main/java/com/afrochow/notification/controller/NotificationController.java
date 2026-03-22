package com.afrochow.notification.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.notification.dto.BroadcastNotificationRequestDto;
import com.afrochow.notification.dto.NotificationDto;
import com.afrochow.common.enums.NotificationType;
import com.afrochow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "APIs for managing user notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // ========== USER ENDPOINTS (Authenticated Users) ==========

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all notifications", description = "Get all notifications for the authenticated user")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getMyNotifications(Authentication authentication) {
        String userPublicId = authentication.getName();
        List<NotificationDto> notifications = notificationService.getUserNotifications(userPublicId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get unread notifications", description = "Get all unread notifications for the authenticated user")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getUnreadNotifications(Authentication authentication) {
        String userPublicId = authentication.getName();
        List<NotificationDto> notifications = notificationService.getUnreadNotifications(userPublicId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get recent notifications", description = "Get notifications from the last 7 days")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getRecentNotifications(Authentication authentication) {
        String userPublicId = authentication.getName();
        List<NotificationDto> notifications = notificationService.getRecentNotifications(userPublicId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get notifications by type", description = "Get notifications filtered by type")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getNotificationsByType(
            Authentication authentication,
            @PathVariable NotificationType type) {
        String userPublicId = authentication.getName();
        List<NotificationDto> notifications = notificationService.getNotificationsByType(userPublicId, type);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get notification statistics", description = "Get notification counts (total, read, unread)")
    public ResponseEntity<ApiResponse<NotificationService.NotificationStats>> getNotificationStats(Authentication authentication) {
        String userPublicId = authentication.getName();
        NotificationService.NotificationStats stats = notificationService.getNotificationStats(userPublicId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark notification as read", description = "Mark a specific notification as read")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsRead(
            Authentication authentication,
            @PathVariable Long notificationId) {
        String userPublicId = authentication.getName();
        NotificationDto notification = notificationService.markAsRead(userPublicId, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", notification));
    }

    @PatchMapping("/{notificationId}/unread")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark notification as unread", description = "Mark a specific notification as unread")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsUnread(
            Authentication authentication,
            @PathVariable Long notificationId) {
        String userPublicId = authentication.getName();
        NotificationDto notification = notificationService.markAsUnread(userPublicId, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as unread", notification));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark all as read", description = "Mark all notifications as read for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
        String userPublicId = authentication.getName();
        notificationService.markAllAsRead(userPublicId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read"));
    }

    @DeleteMapping("/{notificationId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete notification", description = "Delete a specific notification")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {
        String userPublicId = authentication.getName();
        notificationService.deleteNotification(userPublicId, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification deleted"));
    }

    @DeleteMapping("/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete all read notifications", description = "Delete all read notifications for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> deleteAllReadNotifications(Authentication authentication) {
        String userPublicId = authentication.getName();
        notificationService.deleteAllReadNotifications(userPublicId);
        return ResponseEntity.ok(ApiResponse.success("All read notifications deleted"));
    }

    // ========== ADMIN ENDPOINTS ==========

    @PostMapping("/admin/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Broadcast notification", description = "Send notification to all users (admin only)")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> broadcastNotification(
            @Valid @RequestBody BroadcastNotificationRequestDto request) {
        List<NotificationDto> notifications = notificationService.broadcastNotification(
                request.getTitle(),
                request.getMessage(),
                request.getType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Notification broadcast successfully", notifications));
    }
}
