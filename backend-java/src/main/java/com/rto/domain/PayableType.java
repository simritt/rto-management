package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `payable_types`). */
@Entity
@Table(name = "payable_types")
@Getter
@Setter
@NoArgsConstructor
public class PayableType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payable_type_id")
    private Long payableTypeId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

}
