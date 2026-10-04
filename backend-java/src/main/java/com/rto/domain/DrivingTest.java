package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `driving_tests`). */
@Entity
@Table(name = "driving_tests")
@Getter
@Setter
@NoArgsConstructor
public class DrivingTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "test_id")
    private Long testId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "test_centre_id", nullable = false)
    private Long testCentreId;

    @Column(name = "examiner_employee_id", nullable = false)
    private Long examinerEmployeeId;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "result", nullable = false)
    private String result = "PENDING";

    @Column(name = "remarks")
    private String remarks;

}
