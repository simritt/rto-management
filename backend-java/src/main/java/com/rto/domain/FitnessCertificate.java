package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `fitness_certificates`). */
@Entity
@Table(name = "fitness_certificates")
@Getter
@Setter
@NoArgsConstructor
public class FitnessCertificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificate_id")
    private Long certificateId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "inspection_id")
    private Long inspectionId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

}
