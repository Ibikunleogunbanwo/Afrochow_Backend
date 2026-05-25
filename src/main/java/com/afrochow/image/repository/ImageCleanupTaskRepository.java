package com.afrochow.image.repository;

import com.afrochow.image.model.ImageCleanupTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageCleanupTaskRepository extends JpaRepository<ImageCleanupTask, Long> {

    boolean existsByImageUrlAndProcessedAtIsNull(String imageUrl);

    List<ImageCleanupTask> findTop25ByProcessedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(Integer maxRetries);
}
