package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `violations`). */
@Entity
@Table(name = "violations")
@Getter
@Setter
@NoArgsConstructor
public class Violation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "violation_id")
    private Long violationId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "driver_citizen_id")
    private Long driverCitizenId;

    @Column(name = "violation_type_id", nullable = false)
    private Long violationTypeId;

    @Column(name = "officer_employee_id", nullable = false)
    private Long officerEmployeeId;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

}
