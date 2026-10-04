package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `test_centres`). */
@Entity
@Table(name = "test_centres")
@Getter
@Setter
@NoArgsConstructor
public class TestCentre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "test_centre_id")
    private Long testCentreId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "centre_name", nullable = false)
    private String centreName;

}
