package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `applicants`). */
@Entity
@Table(name = "applicants")
@Getter
@Setter
@NoArgsConstructor
public class Applicant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_id")
    private Long applicantId;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "preferred_office_id")
    private Long preferredOfficeId;

}
