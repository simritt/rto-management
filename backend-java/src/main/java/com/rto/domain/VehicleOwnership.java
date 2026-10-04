package com.rto.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `vehicle_ownerships`). */
@Entity
@Table(name = "vehicle_ownerships")
@Getter
@Setter
@NoArgsConstructor
public class VehicleOwnership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ownership_id")
    private Long ownershipId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "citizen_id", nullable = false)
    private Long citizenId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "current_flag", insertable = false, updatable = false)
    @JsonIgnore
    private Integer currentFlag;

}
