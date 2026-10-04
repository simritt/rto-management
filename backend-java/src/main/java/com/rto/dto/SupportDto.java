package com.rto.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class SupportDto {
    private SupportDto() {}

    public record ComplaintCreate(Long citizenId, @NotNull Long officeId, @NotNull @Size(min = 3, max = 150) String subject,
                                  @NotNull @Size(min = 5, max = 1000) String description) {}

    public record ComplaintStatusChange(@NotNull @Pattern(regexp = "^(OPEN|IN_PROGRESS|RESOLVED|CLOSED)$") String status,
                                        @Size(max = 500) String note) {}

    public record AppealCreate(Long citizenId, @NotNull @Pattern(regexp = "^(CHALLAN|APPLICATION_REJECTION|LICENCE_SUSPENSION)$") String againstType,
                               @NotNull Long againstId, @NotNull @Size(min = 10, max = 1000) String grounds) {}

    public record AppealDecision(@Size(max = 500) String note) {}

    public record MoneyCount(long count, BigDecimal amount) {}

    public record PaymentTotals(Map<String, MoneyCount> byStatus, BigDecimal grossCollected, BigDecimal refunded, BigDecimal netCollected) {}

    public record DashboardSummary(LocalDate asOf, int expiryWindowDays, long totalCitizens, long totalVehicles, long activeLicences,
                                   Map<String, Long> applicationsByStatus, long pendingApplications, long applicationsUnderVerification,
                                   long appointmentsToday, long licencesExpiringSoon, long fitnessExpiringSoon, long pollutionExpiringSoon,
                                   long insuranceExpiringSoon, MoneyCount unpaidChallans, long overdueRoadTax, long activePermits,
                                   long openComplaints, long pendingAppeals, PaymentTotals payments) {}
}
