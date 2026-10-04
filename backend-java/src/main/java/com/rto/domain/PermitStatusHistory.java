package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `permit_status_history`). */
@Entity
@Table(name = "permit_status_history")
@Getter
@Setter
@NoArgsConstructor
public class PermitStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "permit_id", nullable = false)
    private Long permitId;

    @Column(name = "previous_status")
    private String previousStatus;

    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "reason")
    private String reason;

    @PrePersist
    void onCreate() {
        if (changedAt == null) changedAt = Clock.now();
    }
}
