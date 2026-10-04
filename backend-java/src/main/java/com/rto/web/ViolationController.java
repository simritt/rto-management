package com.rto.web;

import com.rto.core.*;
import com.rto.domain.Challan;
import com.rto.domain.ChallanStatusHistory;
import com.rto.dto.ViolationDto.*;
import com.rto.service.ViolationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "violations & challans")
public class ViolationController {
    private final ViolationService svc;

    public ViolationController(ViolationService svc) {
        this.svc = svc;
    }

    @PostMapping("/violations")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("violation.create")
    @Operation(summary = "Record a violation against a vehicle (driver_citizen_id may be omitted)")
    public ViolationOut create(@Valid @RequestBody ViolationCreate body, CurrentUser user) {
        return svc.create(body, user);
    }

    @GetMapping("/violations")
    @Requires("violation.view")
    public PageResponse<ViolationOut> list(@RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                           @RequestParam(name = "driver_citizen_id", required = false) Long driverCitizenId,
                                           @RequestParam(name = "violation_type_id", required = false) Long violationTypeId,
                                           @RequestParam(name = "officer_id", required = false) Long officerId,
                                           @RequestParam(name = "date_from", required = false) LocalDate from,
                                           @RequestParam(name = "date_to", required = false) LocalDate to,
                                           @RequestParam(required = false) Boolean unchallaned, PageParams p) {
        return svc.list(p, vehicleId, driverCitizenId, violationTypeId, officerId, from, to, unchallaned);
    }

    @GetMapping("/violations/{violationId}")
    @Requires("violation.view")
    public ViolationOut get(@PathVariable Long violationId) {
        return svc.view(violationId);
    }

    @PostMapping("/challans")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("challan.create")
    @Operation(summary = "Issue a challan bundling violations; total is computed from the violation types' fines")
    public ChallanDetail createChallan(@Valid @RequestBody ChallanCreate body, CurrentUser user) {
        return svc.create(body, user);
    }

    @GetMapping("/challans")
    @Requires("challan.view")
    public PageResponse<Challan> challans(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                          @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                          @RequestParam(name = "driver_citizen_id", required = false) Long driverCitizenId,
                                          @RequestParam(name = "date_from", required = false) LocalDate from,
                                          @RequestParam(name = "date_to", required = false) LocalDate to, PageParams p) {
        return svc.list(p, status, vehicleId, driverCitizenId, from, to);
    }

    @GetMapping("/challans/{challanId}")
    @Requires("challan.view")
    public ChallanDetail challan(@PathVariable Long challanId) {
        return svc.viewChallan(challanId);
    }

    @GetMapping("/challans/{challanId}/history")
    @Requires("challan.view")
    public List<ChallanStatusHistory> history(@PathVariable Long challanId) {
        return svc.history(challanId);
    }

    @PostMapping("/challans/{challanId}/violations")
    @Requires("challan.manage")
    @Operation(summary = "Attach more violations (no duplicates; total recalculated)")
    public ChallanDetail addViolations(@PathVariable Long challanId, @Valid @RequestBody AddViolations body, CurrentUser user) {
        return svc.addViolations(challanId, body.violationIds(), user);
    }

    @PostMapping("/challans/{challanId}/dispute")
    @Requires("challan.manage")
    @Operation(summary = "ISSUED -> DISPUTED")
    public ChallanDetail dispute(@PathVariable Long challanId, @Valid @RequestBody ChallanReason body, CurrentUser user) {
        return svc.changeStatus(challanId, "DISPUTED", body.reason(), user);
    }

    @PostMapping("/challans/{challanId}/reissue")
    @Requires("challan.manage")
    @Operation(summary = "DISPUTED -> ISSUED (dispute dismissed)")
    public ChallanDetail reissue(@PathVariable Long challanId, @Valid @RequestBody ChallanReason body, CurrentUser user) {
        return svc.changeStatus(challanId, "ISSUED", body.reason(), user);
    }

    @PostMapping("/challans/{challanId}/cancel")
    @Requires("challan.manage")
    @Operation(summary = "ISSUED/DISPUTED -> CANCELLED (violations become available again)")
    public ChallanDetail cancel(@PathVariable Long challanId, @Valid @RequestBody ChallanReason body, CurrentUser user) {
        return svc.changeStatus(challanId, "CANCELLED", body.reason(), user);
    }
}
