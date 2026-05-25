package com.afrochow.outbox.repository;

import com.afrochow.outbox.enums.OutboxStatus;
import com.afrochow.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(
            value = """
                    SELECT *
                    FROM outbox_event
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> findPendingForUpdateSkipLocked(@Param("limit") int limit);

    @Modifying
    @Query(
            value = """
                    UPDATE outbox_event
                    SET status = 'PENDING',
                        claimed_at = NULL
                    WHERE status = 'PROCESSING'
                      AND claimed_at < :cutoff
                    """,
            nativeQuery = true
    )
    int resetStaleProcessingEvents(@Param("cutoff") LocalDateTime cutoff);

    long countByStatus(OutboxStatus status);
}
