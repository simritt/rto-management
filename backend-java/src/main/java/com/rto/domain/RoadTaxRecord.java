package com.rto.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `road_tax_records`). */
@Entity
@Table(name = "road_tax_records")
@Getter
@Setter
@NoArgsConstructor
public class RoadTaxRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tax_record_id")
    private Long taxRecordId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "assessment_year", nullable = false)
    private Integer assessmentYear;

    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "status", nullable = false)
    private String status = "DUE";

}
