package com.afrochow.image.service;

import com.afrochow.image.ImageUploadService;
import com.afrochow.image.model.ImageCleanupTask;
import com.afrochow.image.repository.ImageCleanupTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCleanupService {

    private final ImageCleanupTaskRepository imageCleanupTaskRepository;
    private final ImageUploadService imageUploadService;

    @Value("${app.upload.cleanup.max-retries:5}")
    private int maxRetries;

    @Transactional
    public void enqueue(String imageUrl, String reason) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        if (imageCleanupTaskRepository.existsByImageUrlAndProcessedAtIsNull(imageUrl)) return;

        imageCleanupTaskRepository.save(ImageCleanupTask.builder()
                .imageUrl(imageUrl)
                .reason(reason != null && !reason.isBlank() ? reason : "unspecified")
                .retryCount(0)
                .build());
    }

    @Scheduled(fixedDelayString = "${app.upload.cleanup.fixed-delay-ms:300000}")
    @Transactional
    public void cleanupPendingImages() {
        List<ImageCleanupTask> tasks =
                imageCleanupTaskRepository.findTop25ByProcessedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(maxRetries);

        for (ImageCleanupTask task : tasks) {
            try {
                imageUploadService.deleteImageOrThrow(task.getImageUrl());
                task.setProcessedAt(LocalDateTime.now());
                task.setLastError(null);
                log.info("image_cleanup.completed taskId={} reason={}",
                        task.getCleanupTaskId(), task.getReason());
            } catch (Exception e) {
                task.setRetryCount(task.getRetryCount() + 1);
                task.setLastError(truncate(e.getMessage(), 1000));
                log.warn("image_cleanup.failed taskId={} retryCount={} reason={} error={}",
                        task.getCleanupTaskId(), task.getRetryCount(), task.getReason(), e.getMessage());
            }
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
