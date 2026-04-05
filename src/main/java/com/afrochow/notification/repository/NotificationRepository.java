package com.afrochow.notification.repository;

import com.afrochow.notification.model.Notification;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Fix 3: paginated version used by the GET /notifications endpoint
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // Kept for internal use (unread, type-filter, recent — still bounded in practice)
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserAndType(User user, NotificationType type);

    List<Notification> findByType(NotificationType type);

    List<Notification> findByUserAndCreatedAtAfterOrderByCreatedAtDesc(
            User user, LocalDateTime since);

    List<Notification> findByUserAndIsReadOrderByCreatedAtDesc(User user, Boolean isRead);

    Long countByUser(User user);

    Long countByUserAndIsRead(User user, Boolean isRead);

    // Bulk delete read notifications — avoids loading into memory
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user = :user AND n.isRead = true")
    void deleteAllReadByUser(@Param("user") User user);

    // Bulk mark-all-read — single UPDATE instead of fetch-loop-saveAll
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.user = :user AND n.isRead = false")
    void markAllAsReadByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    // Fix 4: single-statement broadcast — avoids loading every User into memory
    @Modifying
    @Query(value = """
            INSERT INTO notification (title, message, type, is_read, created_at, user_id)
            SELECT :title, :message, :type, 0, NOW(), u.user_id FROM users u
            """, nativeQuery = true)
    void insertBroadcastToAllUsers(@Param("title") String title,
                                   @Param("message") String message,
                                   @Param("type") String type);
}
