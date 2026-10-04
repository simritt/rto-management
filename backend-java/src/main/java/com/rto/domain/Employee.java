package com.rto.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `employees`). */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "designation_id", nullable = false)
    private Long designationId;

    @Column(name = "date_joined", nullable = false)
    private LocalDate dateJoined;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

}
