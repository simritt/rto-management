package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `vehicle_inspections`). */
@Entity
@Table(name = "vehicle_inspections")
@Getter
@Setter
@NoArgsConstructor
public class VehicleInspection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "inspector_employee_id", nullable = false)
    private Long inspectorEmployeeId;

    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "remarks")
    private String remarks;

}
