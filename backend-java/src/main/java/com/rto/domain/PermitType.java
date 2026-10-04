package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `permit_types`). */
@Entity
@Table(name = "permit_types")
@Getter
@Setter
@NoArgsConstructor
public class PermitType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permit_type_id")
    private Long permitTypeId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(name = "validity_months", nullable = false)
    private Integer validityMonths;

}
