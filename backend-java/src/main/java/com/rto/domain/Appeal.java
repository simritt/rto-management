package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `appeals`). */
@Entity
@Table(name = "appeals")
@Getter
@Setter
@NoArgsConstructor
public class Appeal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appeal_id")
    private Long appealId;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "against_type", nullable = false)
    private String againstType;

    @Column(name = "against_id", nullable = false)
    private Long againstId;

    @Column(name = "grounds", nullable = false)
    private String grounds;

    @Column(name = "status", nullable = false)
    private String status = "FILED";

    @Column(name = "filed_at", nullable = false)
    private LocalDateTime filedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        if (filedAt == null) filedAt = Clock.now();
    }
}
