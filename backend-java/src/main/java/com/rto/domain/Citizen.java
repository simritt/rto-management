package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `citizens`). */
@Entity
@Table(name = "citizens")
@Getter
@Setter
@NoArgsConstructor
public class Citizen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "citizen_id")
    private Long citizenId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "citizen_code", nullable = false)
    private String citizenCode;

    @Column(name = "blacklisted", nullable = false)
    private Boolean blacklisted = Boolean.FALSE;

    @Column(name = "blacklist_reason")
    private String blacklistReason;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    void onCreate() {
        if (registeredAt == null) registeredAt = Clock.now();
    }
}
