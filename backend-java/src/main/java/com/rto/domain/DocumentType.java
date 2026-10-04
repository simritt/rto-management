package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `document_types`). */
@Entity
@Table(name = "document_types")
@Getter
@Setter
@NoArgsConstructor
public class DocumentType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_type_id")
    private Long documentTypeId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(name = "is_mandatory_default", nullable = false)
    private Boolean isMandatoryDefault = Boolean.TRUE;

    @Column(name = "validity_period_days")
    private Integer validityPeriodDays;

}
