package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `rto_offices`). */
@Entity
@Table(name = "rto_offices")
@Getter
@Setter
@NoArgsConstructor
public class RtoOffice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(name = "office_name", nullable = false)
    private String officeName;

    @Column(name = "office_code", nullable = false)
    private String officeCode;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

}
