package com.afrochow.notification.repository;

import com.afrochow.notification.model.BroadcastLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BroadcastLogRepository extends JpaRepository<BroadcastLog, Long> {

    Page<BroadcastLog> findAllByOrderBySentAtDesc(Pageable pageable);
}
