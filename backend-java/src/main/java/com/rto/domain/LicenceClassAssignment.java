package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `licence_class_assignments`). */
@Entity
@Table(name = "licence_class_assignments")
@Getter
@Setter
@NoArgsConstructor
@IdClass(LicenceClassAssignment.Pk.class)
public class LicenceClassAssignment {
    @Id
    @Column(name = "driving_licence_id")
    private Long drivingLicenceId;

    @Id
    @Column(name = "licence_class_id")
    private Long licenceClassId;

    @Column(name = "granted_on", nullable = false)
    private LocalDate grantedOn;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private Long drivingLicenceId;
        private Long licenceClassId;
    }
}
