package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `applications`). */
@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "application_number", nullable = false)
    private String applicationNumber;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "service_type_id", nullable = false)
    private Long serviceTypeId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "assigned_officer_id")
    private Long assignedOfficerId;

    @Column(name = "current_status", nullable = false)
    private String currentStatus = "SUBMITTED";

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "remarks")
    private String remarks;

    @PrePersist
    void onCreate() {
        if (submittedAt == null) submittedAt = Clock.now();
    }
}
