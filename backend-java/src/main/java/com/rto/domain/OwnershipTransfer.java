package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `ownership_transfers`). */
@Entity
@Table(name = "ownership_transfers")
@Getter
@Setter
@NoArgsConstructor
public class OwnershipTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "from_citizen_id", nullable = false)
    private Long fromCitizenId;

    @Column(name = "to_citizen_id", nullable = false)
    private Long toCitizenId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = Clock.now();
    }
}
