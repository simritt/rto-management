package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `appointment_slots`). */
@Entity
@Table(name = "appointment_slots")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "counter_id")
    private Long counterId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "capacity", nullable = false)
    private Integer capacity = 1;

    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount = 0;

}
