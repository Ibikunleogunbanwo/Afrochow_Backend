package com.afrochow.user.job;

import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled background job that permanently deletes accounts after the
 * 30-day soft-deletion grace period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionCleanupJob {

    private static final int DELETION_GRACE_PERIOD_DAYS = 30;

    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 2 * * *") // 02:00 UTC every day
    @Transactional
    public void purgeExpiredAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(DELETION_GRACE_PERIOD_DAYS);
        List<User> expired = userRepository.findByScheduledForDeletionAtBefore(cutoff);

        if (expired.isEmpty()) {
            return;
        }

        userRepository.deleteAll(expired);
        log.info("Purged {} account(s) that passed the {}-day deletion window",
                expired.size(), DELETION_GRACE_PERIOD_DAYS);
    }
}
