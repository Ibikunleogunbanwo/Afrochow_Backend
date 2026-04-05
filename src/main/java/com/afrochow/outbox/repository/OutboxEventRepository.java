package com.afrochow.outbox.repository;

import com.afrochow.outbox.enums.OutboxStatus;
import com.afrochow.outbox.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Fetch a batch of PENDING events ordered by creation time.
     *
     * PESSIMISTIC_WRITE + timeout=0 translates to SELECT ... FOR UPDATE SKIP LOCKED
     * in MySQL — rows already claimed by another poller thread/instance are skipped,
     * preventing double-dispatch under horizontal scale.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findAndLockPendingEvents(Pageable pageable);

    long countByStatus(OutboxStatus status);
}
