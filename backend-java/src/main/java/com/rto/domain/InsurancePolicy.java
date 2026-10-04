package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `insurance_policies`). */
@Entity
@Table(name = "insurance_policies")
@Getter
@Setter
@NoArgsConstructor
public class InsurancePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

}
