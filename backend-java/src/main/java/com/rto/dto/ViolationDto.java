package com.rto.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ViolationDto {
    private ViolationDto() {}

    public record ViolationCreate(@NotNull Long vehicleId, Long driverCitizenId, @NotNull Long violationTypeId, Long officerEmployeeId,
                                  @NotBlank @Size(max = 200) String location, @NotNull LocalDateTime occurredAt) {}

    public record ViolationOut(Long violationId, Long vehicleId, String registrationNumber, Long driverCitizenId, Long violationTypeId,
                               String violationDescription, BigDecimal fineAmount, Long officerEmployeeId, String location,
                               LocalDateTime occurredAt, Long challanId) {}

    public record ChallanCreate(@NotNull @Size(min = 1) List<Long> violationIds,
                                @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9/_-]+$") String challanNumber) {}

    public record AddViolations(@NotNull @Size(min = 1) List<Long> violationIds) {}

    public record ChallanReason(@NotNull @Size(min = 3, max = 255) String reason) {}

    public record ChallanDetail(Long challanId, String challanNumber, BigDecimal totalAmount, String status, LocalDateTime issuedAt,
                                Long vehicleId, String registrationNumber, List<ViolationOut> violations) {}
}
