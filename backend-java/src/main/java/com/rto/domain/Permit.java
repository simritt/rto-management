package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `permits`). */
@Entity
@Table(name = "permits")
@Getter
@Setter
@NoArgsConstructor
public class Permit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permit_id")
    private Long permitId;

    @Column(name = "permit_number", nullable = false)
    private String permitNumber;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "permit_type_id", nullable = false)
    private Long permitTypeId;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

}
