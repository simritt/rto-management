package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `complaints`). */
@Entity
@Table(name = "complaints")
@Getter
@Setter
@NoArgsConstructor
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "complaint_id")
    private Long complaintId;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    @Column(name = "filed_at", nullable = false)
    private LocalDateTime filedAt;

    @PrePersist
    void onCreate() {
        if (filedAt == null) filedAt = Clock.now();
    }
}
