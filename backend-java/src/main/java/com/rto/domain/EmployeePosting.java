package com.rto.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `employee_postings`). */
@Entity
@Table(name = "employee_postings")
@Getter
@Setter
@NoArgsConstructor
public class EmployeePosting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "posting_id")
    private Long postingId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "posted_from", nullable = false)
    private LocalDate postedFrom;

    @Column(name = "posted_to")
    private LocalDate postedTo;

    @Column(name = "current_flag", insertable = false, updatable = false)
    @JsonIgnore
    private Integer currentFlag;

}
