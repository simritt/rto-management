package com.rto.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `violation_types`). */
@Entity
@Table(name = "violation_types")
@Getter
@Setter
@NoArgsConstructor
public class ViolationType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "violation_type_id")
    private Long violationTypeId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "base_fine_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFineAmount;

    @Column(name = "is_cognizable", nullable = false)
    private Boolean isCognizable = Boolean.FALSE;

}
