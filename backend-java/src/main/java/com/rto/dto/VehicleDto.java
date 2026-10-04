package com.rto.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class VehicleDto {
    private VehicleDto() {}

    public static final String REG = "^[A-Za-z0-9 -]{4,20}$";
    public static final String VIN = "^[A-Za-z0-9-]{5,40}$";
    public static final String STATUS = "^(ACTIVE|BLACKLISTED|SCRAPPED|DEREGISTERED)$";

    public record VehicleCreate(@NotNull @Pattern(regexp = REG) String registrationNumber,
                                @NotNull @Pattern(regexp = VIN) String chassisNumber, @NotNull @Pattern(regexp = VIN) String engineNumber,
                                @NotNull Long manufacturerId, @NotNull Long modelId, @NotNull Long vehicleTypeId, @NotNull Long fuelTypeId,
                                @NotNull @Min(1900) @Max(2100) Integer manufactureYear, @Size(max = 30) String color,
                                @NotNull Long registeringOfficeId, LocalDate registrationDate, Long ownerCitizenId) {}

    public record VehicleUpdate(@Size(max = 30) String color, @Pattern(regexp = STATUS) String status,
                                @Size(min = 3, max = 255) String reason) {}

    public record OwnerBrief(Long citizenId, String citizenCode, String name, LocalDate effectiveFrom) {}

    public record VehicleOut(Long vehicleId, String registrationNumber, String chassisNumber, String engineNumber,
                             Long manufacturerId, String manufacturerName, Long modelId, String modelName, Long vehicleTypeId,
                             String vehicleTypeName, Long fuelTypeId, String fuelTypeName, Integer manufactureYear, String color,
                             Long registeringOfficeId, LocalDate registrationDate, String status, OwnerBrief currentOwner) {}

    public record OwnershipOut(Long ownershipId, Long vehicleId, Long citizenId, String citizenCode, String citizenName,
                               LocalDate effectiveFrom, LocalDate effectiveTo, Boolean isCurrent) {}

    public record AddOwner(@NotNull Long citizenId, LocalDate effectiveFrom) {}

    public record TransferCreate(@NotNull Long vehicleId, @NotNull Long toCitizenId, @NotNull Long applicationId, Long fromCitizenId) {}

    public record TransferApprove(LocalDate transferDate) {}

    public record TransferReject(@NotNull @Size(min = 3, max = 255) String reason) {}

    public record CitizenVehicles(List<java.util.Map<String, Object>> vehicles) {}
}
