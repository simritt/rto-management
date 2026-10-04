package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `counters`). */
@Entity
@Table(name = "counters")
@Getter
@Setter
@NoArgsConstructor
public class Counter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counter_id")
    private Long counterId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "counter_number", nullable = false)
    private String counterNumber;

    @Column(name = "service_category")
    private String serviceCategory;

}
