package com.rto.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ComplianceDto {
    private ComplianceDto() {}

    public record InspectionCreate(@NotNull Long vehicleId, Long inspectorEmployeeId, LocalDateTime inspectedAt,
                                   @NotNull @Pattern(regexp = "^(PASS|FAIL)$") String result, @Size(max = 255) String remarks) {}

    public record FitnessCreate(@NotNull Long vehicleId, Long inspectionId, @NotNull LocalDate issueDate, @NotNull LocalDate expiryDate) {}

    public record PollutionCreate(@NotNull Long vehicleId, @NotNull LocalDate issueDate, @NotNull LocalDate expiryDate) {}

    public record InsuranceCreate(@NotNull Long vehicleId, @NotBlank @Size(max = 100) String providerName,
                                  @NotNull @Size(min = 3, max = 40) @Pattern(regexp = "^[A-Za-z0-9/_-]+$") String policyNumber,
                                  @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

    public record RoadTaxCreate(@NotNull Long vehicleId, @NotNull @Min(1990) @Max(2100) Integer assessmentYear,
                                @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal amountDue,
                                @NotNull LocalDate dueDate) {}

    public record Reason(@NotNull @Size(min = 3, max = 255) String reason) {}

    public record ComplianceItem(Boolean required, Boolean valid, String detail, LocalDate expiryDate, Integer daysToExpiry, Long recordId) {}

    public record ComplianceSummary(Long vehicleId, String registrationNumber, LocalDate asOf, String overall,
                                    ComplianceItem fitness, ComplianceItem pollution, ComplianceItem insurance, ComplianceItem roadTax) {}

    public record ExpiringItem(String kind, Long recordId, Long vehicleId, String registrationNumber, LocalDate expiryDate, Integer daysLeft) {}
}
