package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `challan_violations`). */
@Entity
@Table(name = "challan_violations")
@Getter
@Setter
@NoArgsConstructor
@IdClass(ChallanViolation.Pk.class)
public class ChallanViolation {
    @Id
    @Column(name = "challan_id")
    private Long challanId;

    @Id
    @Column(name = "violation_id")
    private Long violationId;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private Long challanId;
        private Long violationId;
    }
}
