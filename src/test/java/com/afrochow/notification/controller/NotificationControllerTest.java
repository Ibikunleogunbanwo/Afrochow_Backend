package com.afrochow.notification.controller;

import com.afrochow.common.enums.NotificationType;
import com.afrochow.notification.dto.BroadcastLogDto;
import com.afrochow.notification.dto.BroadcastNotificationRequestDto;
import com.afrochow.notification.dto.NotificationDto;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for NotificationController.
 *
 * User endpoints take a plain {@code Authentication} parameter (covered via
 * {@code authenticatedAs}). Admin broadcast endpoints are guarded by
 * {@code @deptAccess.can('BROADCAST')}, which is not exercised in this slice
 * (see ControllerSliceTest javadoc) — those tests only verify routing,
 * validation, and response shape.
 */
@ControllerSliceTest(NotificationController.class)
class NotificationControllerTest extends AbstractControllerTest {

    @MockitoBean private NotificationService notificationService;

    private static final String USERNAME = "user-1";

    private NotificationDto sampleNotification(Long id, boolean isRead) {
        return NotificationDto.builder()
                .notificationId(id)
                .title("Order update")
                .message("Your order is on its way")
                .type(NotificationType.ORDER_UPDATE)
                .isRead(isRead)
                .build();
    }

    @Test
    void getMyNotifications_returns200WithPage() throws Exception {
        Page<NotificationDto> page = new PageImpl<>(List.of(sampleNotification(1L, false)), PageRequest.of(0, 20), 1);
        when(notificationService.getUserNotifications(eq(USERNAME), any())).thenReturn(page);

        mockMvc.perform(get("/notifications")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].notificationId").value(1));
    }

    @Test
    void getUnreadNotifications_returns200() throws Exception {
        when(notificationService.getUnreadNotifications(USERNAME))
                .thenReturn(List.of(sampleNotification(1L, false)));

        mockMvc.perform(get("/notifications/unread")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isRead").value(false));
    }

    @Test
    void getRecentNotifications_returns200() throws Exception {
        when(notificationService.getRecentNotifications(USERNAME))
                .thenReturn(List.of(sampleNotification(1L, true)));

        mockMvc.perform(get("/notifications/recent")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getNotificationsByType_returns200() throws Exception {
        when(notificationService.getNotificationsByType(USERNAME, NotificationType.PROMO))
                .thenReturn(List.of(sampleNotification(1L, false)));

        mockMvc.perform(get("/notifications/type/{type}", "PROMO")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("ORDER_UPDATE"));
    }

    @Test
    void getNotificationsByType_invalidType_returns400() throws Exception {
        mockMvc.perform(get("/notifications/type/{type}", "NOT_A_TYPE")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNotificationStats_returns200() throws Exception {
        NotificationService.NotificationStats stats = NotificationService.NotificationStats.builder()
                .totalNotifications(10L)
                .unreadNotifications(3L)
                .readNotifications(7L)
                .build();
        when(notificationService.getNotificationStats(USERNAME)).thenReturn(stats);

        mockMvc.perform(get("/notifications/stats")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadNotifications").value(3));
    }

    @Test
    void markAsRead_returns200() throws Exception {
        when(notificationService.markAsRead(USERNAME, 1L)).thenReturn(sampleNotification(1L, true));

        mockMvc.perform(patch("/notifications/{notificationId}/read", 1L)
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    void markAsUnread_returns200() throws Exception {
        when(notificationService.markAsUnread(USERNAME, 1L)).thenReturn(sampleNotification(1L, false));

        mockMvc.perform(patch("/notifications/{notificationId}/unread", 1L)
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(false));
    }

    @Test
    void markAllAsRead_returns200() throws Exception {
        doNothing().when(notificationService).markAllAsRead(USERNAME);

        mockMvc.perform(patch("/notifications/read-all")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteNotification_returns200() throws Exception {
        doNothing().when(notificationService).deleteNotification(USERNAME, 1L);

        mockMvc.perform(delete("/notifications/{notificationId}", 1L)
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteAllReadNotifications_returns200() throws Exception {
        doNothing().when(notificationService).deleteAllReadNotifications(USERNAME);

        mockMvc.perform(delete("/notifications/read")
                        .with(authenticatedAs(USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void broadcastNotification_valid_returns202() throws Exception {
        doNothing().when(notificationService).enqueueBroadcast(any(BroadcastNotificationRequestDto.class), eq("admin-1"));

        BroadcastNotificationRequestDto request = BroadcastNotificationRequestDto.builder()
                .title("Big sale")
                .message("50% off this weekend")
                .type(NotificationType.PROMO)
                .build();

        mockMvc.perform(post("/notifications/admin/broadcast")
                        .with(authenticatedAs("admin-1", "ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void broadcastNotification_missingTitle_returns400WithValidationErrors() throws Exception {
        BroadcastNotificationRequestDto request = BroadcastNotificationRequestDto.builder()
                .message("50% off this weekend")
                .type(NotificationType.PROMO)
                .build();

        mockMvc.perform(post("/notifications/admin/broadcast")
                        .with(authenticatedAs("admin-1", "ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).enqueueBroadcast(any(), any());
    }

    @Test
    void getBroadcastHistory_returns200WithPage() throws Exception {
        BroadcastLogDto log = BroadcastLogDto.builder()
                .id(1L)
                .title("Big sale")
                .message("50% off")
                .type(NotificationType.PROMO)
                .targetAudience("ALL")
                .recipientCount(500)
                .sentBy("admin-1")
                .build();
        Page<BroadcastLogDto> page = new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1);
        when(notificationService.getBroadcastHistory(any())).thenReturn(page);

        mockMvc.perform(get("/notifications/admin/broadcasts")
                        .with(authenticatedAs("admin-1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].recipientCount").value(500));
    }
}
