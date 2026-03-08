package com.afrochow.notification.repository;
import com.afrochow.notification.model.Notification;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser(User user);

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserAndType(User user, NotificationType type);

    List<Notification> findByType(NotificationType type);

    List<Notification> findByUserAndCreatedAtAfterOrderByCreatedAtDesc(
            User user, LocalDateTime since);

    List<Notification> findByUserAndIsReadOrderByCreatedAtDesc(User user, Boolean isRead);

    Long countByUser(User user);

    Long countByUserAndIsRead(User user, Boolean isRead);
}
