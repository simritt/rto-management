package com.rto.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `learner_licences`). */
@Entity
@Table(name = "learner_licences")
@Getter
@Setter
@NoArgsConstructor
public class LearnerLicence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "learner_licence_id")
    private Long learnerLicenceId;

    @Column(name = "licence_number", nullable = false)
    private String licenceNumber;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "active_flag", insertable = false, updatable = false)
    @JsonIgnore
    private Integer activeFlag;

}
