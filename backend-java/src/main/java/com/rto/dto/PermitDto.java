package com.rto.dto;

import com.rto.domain.RouteSegment;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class PermitDto {
    private PermitDto() {}

    public record RouteCreate(@NotBlank @Size(max = 120) String routeName, @NotBlank @Size(max = 80) String origin,
                              @NotBlank @Size(max = 80) String destination) {}

    public record SegmentCreate(@NotBlank @Size(max = 120) String segmentName, @Min(1) @Max(65535) Integer sequenceNo) {}

    public record RouteDetail(Long routeId, String routeName, String origin, String destination, List<RouteSegment> segments) {}

    public record PermitCreate(@NotNull Long vehicleId, @NotNull Long citizenId, @NotNull Long permitTypeId, Long routeId,
                               @NotNull Long applicationId,
                               @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9/_-]+$") String permitNumber,
                               LocalDate issueDate, LocalDate expiryDate) {}

    public record PermitReason(@NotNull @Size(min = 3, max = 255) String reason) {}
}
