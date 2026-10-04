package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `licence_status_history`). */
@Entity
@Table(name = "licence_status_history")
@Getter
@Setter
@NoArgsConstructor
public class LicenceStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "driving_licence_id", nullable = false)
    private Long drivingLicenceId;

    @Column(name = "previous_status")
    private String previousStatus;

    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "reason")
    private String reason;

    @PrePersist
    void onCreate() {
        if (changedAt == null) changedAt = Clock.now();
    }
}
