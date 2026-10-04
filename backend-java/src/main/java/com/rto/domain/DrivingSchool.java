package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `driving_schools`). */
@Entity
@Table(name = "driving_schools")
@Getter
@Setter
@NoArgsConstructor
public class DrivingSchool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "school_name", nullable = false)
    private String schoolName;

    @Column(name = "licence_number", nullable = false)
    private String licenceNumber;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

}
