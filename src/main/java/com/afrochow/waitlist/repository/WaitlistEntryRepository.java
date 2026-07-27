package com.afrochow.waitlist.repository;

import com.afrochow.waitlist.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    Optional<WaitlistEntry> findByEmailAndRole(String email, String role);
}
