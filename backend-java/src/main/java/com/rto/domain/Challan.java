package com.rto.domain;

import com.rto.core.Clock;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `challans`). */
@Entity
@Table(name = "challans")
@Getter
@Setter
@NoArgsConstructor
public class Challan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challan_id")
    private Long challanId;

    @Column(name = "challan_number", nullable = false)
    private String challanNumber;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false)
    private String status = "ISSUED";

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) issuedAt = Clock.now();
    }
}
