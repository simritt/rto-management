package com.rto.dto;

import com.rto.dto.IdentityDto.PersonFields;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public final class OrgDto {
    private OrgDto() {}

    public record OfficeCreate(@NotNull Long regionId, @NotBlank @Size(max = 100) String officeName,
                               @NotNull @Size(min = 2, max = 15) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String officeCode,
                               @NotBlank @Size(max = 200) String addressLine, Boolean isActive) {
        public OfficeCreate {
            isActive = isActive == null ? Boolean.TRUE : isActive;
        }
    }

    public record OfficeUpdate(Long regionId, @Size(min = 1, max = 100) String officeName,
                               @Size(min = 1, max = 200) String addressLine, Boolean isActive) {}

    public record CounterCreate(@NotBlank @Size(max = 10) String counterNumber, @Size(max = 50) String serviceCategory) {}

    public record CounterUpdate(@Size(min = 1, max = 10) String counterNumber, @Size(max = 50) String serviceCategory) {}

    public record DepartmentIn(@NotBlank @Size(max = 80) String departmentName) {}

    public record DesignationIn(@NotBlank @Size(max = 80) String title, @NotNull @Min(1) @Max(255) Integer rankLevel) {}

    public record DesignationUpdate(@Size(min = 1, max = 80) String title, @Min(1) @Max(255) Integer rankLevel) {}

    public record PostingCreate(@NotNull Long officeId, @NotNull Long departmentId, @NotNull LocalDate postedFrom) {}

    public record PostingOut(Long postingId, Long employeeId, Long officeId, Long departmentId, LocalDate postedFrom,
                             LocalDate postedTo, Boolean isCurrent) {}

    public record EmployeeCreate(Long personId, @Valid PersonFields person,
                                 @Size(min = 3, max = 20) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String employeeCode,
                                 @NotNull Long designationId, @NotNull LocalDate dateJoined, @Valid PostingCreate posting) {}

    public record EmployeeUpdate(Long designationId, Boolean isActive) {}

    public record EmployeeOut(Long employeeId, Long personId, String employeeCode, String firstName, String lastName,
                              Long designationId, String designationTitle, Integer rankLevel, LocalDate dateJoined,
                              Boolean isActive, PostingOut currentPosting) {}
}
