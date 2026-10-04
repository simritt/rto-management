package com.rto.web;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.ComplianceDto.*;
import com.rto.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "compliance")
public class ComplianceController {
    private final ComplianceService svc;
    private final Db db;

    public ComplianceController(ComplianceService svc, Db db) {
        this.svc = svc;
        this.db = db;
    }

    @GetMapping("/vehicles/{vehicleId}/compliance")
    @Requires("compliance.view")
    @Operation(summary = "Consolidated compliance status (fitness, pollution, insurance, road tax)")
    public ComplianceSummary compliance(@PathVariable Long vehicleId) {
        return svc.summary(vehicleId);
    }

    @GetMapping("/compliance/expiring")
    @Requires("compliance.view")
    @Operation(summary = "Fitness/pollution/insurance expiring within `days` (default 30)")
    public PageResponse<ExpiringItem> expiring(@RequestParam(defaultValue = "30") @Min(0) @Max(3650) int days,
                                               @RequestParam(required = false) @Pattern(regexp = "^(FITNESS|POLLUTION|INSURANCE)$") String kind, PageParams p) {
        return svc.expiring(p, days, kind);
    }

    @PostMapping("/compliance/refresh-statuses")
    @Requires("compliance.manage")
    @Operation(summary = "Persist time-driven changes: lapsed certificates -> EXPIRED, past-due tax -> OVERDUE")
    public Map<String, Object> refresh(CurrentUser user) {
        return svc.refreshStatuses(user);
    }

    // ---- inspections ------------------------------------------------------------------------------------

    @PostMapping("/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("compliance.manage")
    public VehicleInspection createInspection(@Valid @RequestBody InspectionCreate body, CurrentUser user) {
        return svc.createInspection(body, user);
    }

    @GetMapping("/inspections")
    @Requires("compliance.view")
    public PageResponse<VehicleInspection> inspections(@RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                                       @RequestParam(required = false) @Pattern(regexp = "^(PASS|FAIL)$") String result, PageParams p) {
        QB q = new QB("i", "VehicleInspection i").eq("i.vehicleId", vehicleId).eq("i.result", result);
        return db.page(q, VehicleInspection.class, p, Map.of("inspected_at", "i.inspectedAt", "inspection_id", "i.inspectionId"), "i.inspectionId", true);
    }

    @GetMapping("/inspections/{inspectionId}")
    @Requires("compliance.view")
    public VehicleInspection inspection(@PathVariable Long inspectionId) {
        return db.get(VehicleInspection.class, inspectionId, "Inspection");
    }

    // ---- fitness ----------------------------------------------------------------------------------------

    @PostMapping("/fitness-certificates")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("compliance.manage")
    public FitnessCertificate createFitness(@Valid @RequestBody FitnessCreate body, CurrentUser user) {
        return svc.createFitness(body, user);
    }

    @GetMapping("/fitness-certificates")
    @Requires("compliance.view")
    public PageResponse<FitnessCertificate> fitness(@RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                                    @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status, PageParams p) {
        QB q = new QB("f", "FitnessCertificate f").eq("f.vehicleId", vehicleId).eq("f.status", status);
        return db.page(q, FitnessCertificate.class, p, Map.of("expiry_date", "f.expiryDate", "issue_date", "f.issueDate"), "f.certificateId", true);
    }

    @GetMapping("/fitness-certificates/{certificateId}")
    @Requires("compliance.view")
    public FitnessCertificate fitnessById(@PathVariable Long certificateId) {
        return db.get(FitnessCertificate.class, certificateId, "Fitness certificate");
    }

    @PostMapping("/fitness-certificates/{certificateId}/revoke")
    @Requires("compliance.manage")
    public FitnessCertificate revoke(@PathVariable Long certificateId, @Valid @RequestBody Reason body, CurrentUser user) {
        return svc.revokeFitness(certificateId, body.reason(), user);
    }

    // ---- pollution --------------------------------------------------------------------------------------

    @PostMapping("/pollution-certificates")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("compliance.manage")
    public PollutionCertificate createPollution(@Valid @RequestBody PollutionCreate body, CurrentUser user) {
        return svc.createPollution(body, user);
    }

    @GetMapping("/pollution-certificates")
    @Requires("compliance.view")
    public PageResponse<PollutionCertificate> pollution(@RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                                        @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status, PageParams p) {
        QB q = new QB("c", "PollutionCertificate c").eq("c.vehicleId", vehicleId).eq("c.status", status);
        return db.page(q, PollutionCertificate.class, p, Map.of("expiry_date", "c.expiryDate"), "c.pucId", true);
    }

    @GetMapping("/pollution-certificates/{pucId}")
    @Requires("compliance.view")
    public PollutionCertificate pollutionById(@PathVariable Long pucId) {
        return db.get(PollutionCertificate.class, pucId, "Pollution certificate");
    }

    // ---- insurance --------------------------------------------------------------------------------------

    @PostMapping("/insurance-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("compliance.manage")
    public InsurancePolicy createInsurance(@Valid @RequestBody InsuranceCreate body, CurrentUser user) {
        return svc.createInsurance(body, user);
    }

    @GetMapping("/insurance-policies")
    @Requires("compliance.view")
    public PageResponse<InsurancePolicy> insurance(@RequestParam(name = "vehicle_id", required = false) Long vehicleId, PageParams p) {
        QB q = new QB("i", "InsurancePolicy i").eq("i.vehicleId", vehicleId);
        return db.page(q, InsurancePolicy.class, p, Map.of("end_date", "i.endDate"), "i.policyId", true);
    }

    @GetMapping("/insurance-policies/{policyId}")
    @Requires("compliance.view")
    public InsurancePolicy insuranceById(@PathVariable Long policyId) {
        return db.get(InsurancePolicy.class, policyId, "Insurance policy");
    }

    // ---- road tax ---------------------------------------------------------------------------------------

    @PostMapping("/road-tax")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("compliance.manage")
    @Operation(summary = "Assess road tax. Settling it is done through POST /payments (payable type ROAD_TAX)")
    public RoadTaxRecord createRoadTax(@Valid @RequestBody RoadTaxCreate body, CurrentUser user) {
        return svc.createRoadTax(body, user);
    }

    @GetMapping("/road-tax")
    @Requires("compliance.view")
    public PageResponse<RoadTaxRecord> roadTax(@RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                               @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                               @RequestParam(name = "assessment_year", required = false) Integer year, PageParams p) {
        QB q = new QB("r", "RoadTaxRecord r").eq("r.vehicleId", vehicleId).eq("r.status", status).eq("r.assessmentYear", year);
        return db.page(q, RoadTaxRecord.class, p, Map.of("due_date", "r.dueDate", "assessment_year", "r.assessmentYear"), "r.taxRecordId", true);
    }

    @GetMapping("/road-tax/{taxRecordId}")
    @Requires("compliance.view")
    public RoadTaxRecord roadTaxById(@PathVariable Long taxRecordId) {
        return db.get(RoadTaxRecord.class, taxRecordId, "Road-tax record");
    }
}
