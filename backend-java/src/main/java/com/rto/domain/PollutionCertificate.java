package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `pollution_certificates`). */
@Entity
@Table(name = "pollution_certificates")
@Getter
@Setter
@NoArgsConstructor
public class PollutionCertificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "puc_id")
    private Long pucId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

}
