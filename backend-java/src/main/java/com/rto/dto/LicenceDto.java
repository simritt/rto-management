package com.rto.dto;

import com.rto.dto.IdentityDto.PersonFields;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class LicenceDto {
    private LicenceDto() {}

    private static final String NUMBER = "^[A-Za-z0-9/_-]+$";

    public record SchoolCreate(@NotBlank @Size(max = 120) String schoolName,
                               @NotNull @Size(min = 2, max = 30) @Pattern(regexp = NUMBER) String licenceNumber, @NotNull Long officeId) {}

    public record SchoolUpdate(@Size(min = 1, max = 120) String schoolName, Long officeId) {}

    public record InstructorCreate(Long personId, @Valid PersonFields person, @NotNull Long licenceClassId) {}

    public record InstructorOut(Long instructorId, Long personId, Long schoolId, Long licenceClassId, String firstName, String lastName) {}

    public record TestCentreCreate(@NotNull Long officeId, @NotBlank @Size(max = 120) String centreName) {}

    public record LearnerCreate(@NotNull Long citizenId, @NotNull Long applicationId, @NotNull Long officeId,
                                @Size(min = 3, max = 30) @Pattern(regexp = NUMBER) String licenceNumber,
                                LocalDate issueDate, LocalDate expiryDate) {}

    public record StatusReason(@NotNull @Size(min = 3, max = 255) String reason) {}

    public record DrivingLicenceCreate(@NotNull Long citizenId, @NotNull Long applicationId, @NotNull Long officeId,
                                       Long learnerLicenceId, @NotNull @Size(min = 1) List<Long> licenceClassIds,
                                       @Size(min = 3, max = 30) @Pattern(regexp = NUMBER) String licenceNumber,
                                       LocalDate issueDate, LocalDate expiryDate) {}

    public record ClassOut(Long licenceClassId, String classCode, LocalDate grantedOn) {}

    public record DrivingLicenceOut(Long drivingLicenceId, String licenceNumber, Long citizenId, Long learnerLicenceId,
                                    Long applicationId, Long officeId, LocalDate issueDate, LocalDate expiryDate,
                                    String currentStatus, List<ClassOut> classes) {}

    public record AddClass(@NotNull Long licenceClassId) {}

    public record RenewRequest(@NotNull LocalDate newExpiryDate, @Size(min = 3, max = 255) String reason) {
        public RenewRequest {
            reason = reason == null ? "Renewal" : reason;
        }
    }

    public record TestCreate(@NotNull Long applicationId, @NotNull Long citizenId, @NotNull Long testCentreId,
                             @NotNull Long examinerEmployeeId, @NotNull LocalDateTime scheduledAt, @Size(max = 255) String remarks) {}

    public record TestResultIn(@NotNull @Pattern(regexp = "^(PASS|FAIL|ABSENT)$") String result, @Size(max = 255) String remarks) {}

    public record ReminderResult(int remindersSent) {}
}
