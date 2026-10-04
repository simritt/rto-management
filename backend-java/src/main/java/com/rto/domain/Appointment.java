package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `appointments`). */
@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "token_number", nullable = false)
    private String tokenNumber;

    @Column(name = "status", nullable = false)
    private String status = "BOOKED";

    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    @PrePersist
    void onCreate() {
        if (bookedAt == null) bookedAt = Clock.now();
    }
}
