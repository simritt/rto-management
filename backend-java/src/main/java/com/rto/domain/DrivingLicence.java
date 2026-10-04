package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `driving_licences`). */
@Entity
@Table(name = "driving_licences")
@Getter
@Setter
@NoArgsConstructor
public class DrivingLicence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "driving_licence_id")
    private Long drivingLicenceId;

    @Column(name = "licence_number", nullable = false)
    private String licenceNumber;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "learner_licence_id")
    private Long learnerLicenceId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "current_status", nullable = false)
    private String currentStatus = "ACTIVE";

}
