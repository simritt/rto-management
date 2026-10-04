package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `vehicles`). */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber;

    @Column(name = "chassis_number", nullable = false)
    private String chassisNumber;

    @Column(name = "engine_number", nullable = false)
    private String engineNumber;

    @Column(name = "manufacturer_id", nullable = false)
    private Long manufacturerId;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "vehicle_type_id", nullable = false)
    private Long vehicleTypeId;

    @Column(name = "fuel_type_id", nullable = false)
    private Long fuelTypeId;

    @Column(name = "manufacture_year", nullable = false)
    private Integer manufactureYear;

    @Column(name = "color")
    private String color;

    @Column(name = "registering_office_id", nullable = false)
    private Long registeringOfficeId;

    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

}
