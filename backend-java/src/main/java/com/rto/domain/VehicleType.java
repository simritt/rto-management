package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `vehicle_types`). */
@Entity
@Table(name = "vehicle_types")
@Getter
@Setter
@NoArgsConstructor
public class VehicleType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_type_id")
    private Long vehicleTypeId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(name = "is_commercial", nullable = false)
    private Boolean isCommercial = Boolean.FALSE;

}
