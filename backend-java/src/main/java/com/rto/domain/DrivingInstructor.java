package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `driving_instructors`). */
@Entity
@Table(name = "driving_instructors")
@Getter
@Setter
@NoArgsConstructor
public class DrivingInstructor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instructor_id")
    private Long instructorId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "licence_class_id", nullable = false)
    private Long licenceClassId;

}
