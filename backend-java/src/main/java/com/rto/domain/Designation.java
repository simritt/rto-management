package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `designations`). */
@Entity
@Table(name = "designations")
@Getter
@Setter
@NoArgsConstructor
public class Designation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "rank_level", nullable = false)
    private Integer rankLevel;

}
