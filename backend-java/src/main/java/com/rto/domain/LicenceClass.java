package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `licence_classes`). */
@Entity
@Table(name = "licence_classes")
@Getter
@Setter
@NoArgsConstructor
public class LicenceClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "licence_class_id")
    private Long licenceClassId;

    @Column(name = "class_code", nullable = false)
    private String classCode;

    @Column(name = "description", nullable = false)
    private String description;

}
