package com.afrochow.waitlist.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "waitlist_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_waitlist_email_role",
                        columnNames = {"email", "role"}
                )
        },
        indexes = {
                @Index(name = "idx_waitlist_email", columnList = "email"),
                @Index(name = "idx_waitlist_role", columnList = "role"),
                @Index(name = "idx_waitlist_city", columnList = "city")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String publicWaitlistId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 120)
    private String city;

    @Column(nullable = false, length = 30)
    private String role;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (publicWaitlistId == null || publicWaitlistId.isBlank()) {
            publicWaitlistId = "WAIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
