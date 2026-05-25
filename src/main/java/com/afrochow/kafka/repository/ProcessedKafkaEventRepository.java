package com.afrochow.kafka.repository;

import com.afrochow.kafka.model.ProcessedKafkaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedKafkaEventRepository extends JpaRepository<ProcessedKafkaEvent, Long> {

    boolean existsByConsumerNameAndEventId(String consumerName, String eventId);
}
