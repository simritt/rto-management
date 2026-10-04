package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `vehicle_manufacturers`). */
@Entity
@Table(name = "vehicle_manufacturers")
@Getter
@Setter
@NoArgsConstructor
public class VehicleManufacturer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manufacturer_id")
    private Long manufacturerId;

    @Column(name = "name", nullable = false)
    private String name;

}
