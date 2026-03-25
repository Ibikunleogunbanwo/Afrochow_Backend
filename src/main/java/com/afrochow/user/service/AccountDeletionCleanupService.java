package com.afrochow.user.service;

import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Permanently deletes accounts that have been soft-deleted for more than 30 days.
 * Runs nightly at 02:00 UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionCleanupService {

    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 2 * * *") // 02:00 UTC every day
    @Transactional
    public void purgeExpiredAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<User> expired = userRepository.findByScheduledForDeletionAtBefore(cutoff);

        if (expired.isEmpty()) {
            return;
        }

        userRepository.deleteAll(expired);
        log.info("Purged {} account(s) that passed the 30-day deletion window", expired.size());
    }
}
