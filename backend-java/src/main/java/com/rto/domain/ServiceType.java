package com.rto.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `service_types`). */
@Entity
@Table(name = "service_types")
@Getter
@Setter
@NoArgsConstructor
public class ServiceType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_type_id")
    private Long serviceTypeId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee = new BigDecimal("0.00");

    @Column(name = "sla_days", nullable = false)
    private Integer slaDays = 7;

}
