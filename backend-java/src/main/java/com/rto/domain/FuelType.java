package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `fuel_types`). */
@Entity
@Table(name = "fuel_types")
@Getter
@Setter
@NoArgsConstructor
public class FuelType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fuel_type_id")
    private Long fuelTypeId;

    @Column(name = "fuel_name", nullable = false)
    private String fuelName;

}
