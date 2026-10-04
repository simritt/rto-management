package com.rto.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class ApplicationDto {
    private ApplicationDto() {}

    public static final String STATUS = "^(SUBMITTED|DOCS_PENDING|UNDER_VERIFICATION|APPOINTMENT_SCHEDULED|AWAITING_PAYMENT|APPROVED|REJECTED|COMPLETED|CANCELLED)$";

    public record ApplicationCreate(Long citizenId, @NotNull Long serviceTypeId, @NotNull Long officeId,
                                    @Size(max = 500) String remarks,
                                    @Size(min = 5, max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String applicationNumber) {}

    public record ApplicationUpdate(@Size(max = 500) String remarks, Long officeId) {}

    public record AssignRequest(Long officerId) {}

    public record StatusChange(@NotNull @Pattern(regexp = STATUS) String status, @Size(max = 255) String reason) {}

    public record ApplicationOut(Long applicationId, String applicationNumber, Long citizenId, String citizenCode,
                                 String citizenName, Long serviceTypeId, String serviceCode, String serviceName,
                                 Long officeId, Long assignedOfficerId, String currentStatus, LocalDateTime submittedAt,
                                 LocalDateTime completedAt, String remarks) {}

    public record ApplicationDetail(Long applicationId, String applicationNumber, Long citizenId, String citizenCode,
                                    String citizenName, Long serviceTypeId, String serviceCode, String serviceName,
                                    Long officeId, Long assignedOfficerId, String currentStatus, LocalDateTime submittedAt,
                                    LocalDateTime completedAt, String remarks, BigDecimal feeDue, Integer slaDays,
                                    Long documentCount, Map<String, Object> appointment, List<String> allowedTransitions) {}

    public record DocumentOut(Long documentId, Long applicationId, Long documentTypeId, String documentTypeName,
                              String filePath, String verificationStatus, String effectiveStatus,
                              Long verifiedByEmployeeId, String rejectionReason, LocalDateTime uploadedAt,
                              LocalDateTime verifiedAt, LocalDateTime expiresAt) {}

    public record RejectDocument(@NotNull @Size(min = 5, max = 255) String reason) {}

    public record SlotCreate(@NotNull Long officeId, Long counterId, @NotNull LocalDate slotDate, @NotNull LocalTime startTime,
                             @NotNull LocalTime endTime, @Min(1) @Max(1000) Integer capacity) {
        public SlotCreate {
            capacity = capacity == null ? 1 : capacity;
        }
    }

    public record SlotUpdate(@NotNull @Min(1) @Max(1000) Integer capacity) {}

    public record SlotOut(Long slotId, Long officeId, Long counterId, LocalDate slotDate, LocalTime startTime,
                          LocalTime endTime, Integer capacity, Integer bookedCount, Integer available) {}

    public record BookAppointment(@NotNull Long slotId) {}

    public record AppointmentStatusChange(@NotNull @Pattern(regexp = "^(NO_SHOW|COMPLETED)$") String status) {}
}
