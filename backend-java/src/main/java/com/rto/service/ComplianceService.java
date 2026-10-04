package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.ComplianceDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.rto.core.Audit.m;

/** Inspections, fitness, pollution, insurance and road-tax records plus the consolidated compliance view. */
@Service
@Transactional
public class ComplianceService {
    private final Db db;
    private final Audit audit;
    private final Common common;

    public ComplianceService(Db db, Audit audit, Common common) {
        this.db = db;
        this.audit = audit;
        this.common = common;
    }

    private Vehicle vehicle(Long id, boolean mustBeActive) {
        Vehicle v = db.get(Vehicle.class, id, "Vehicle");
        if (mustBeActive && !"ACTIVE".equals(v.getStatus())) {
            throw ApiException.conflict("VEHICLE_NOT_ACTIVE", "The vehicle is " + v.getStatus() + "; compliance records can only be added to ACTIVE vehicles");
        }
        return v;
    }

    private static void ordered(LocalDate start, LocalDate end, String field) {
        if (!end.isAfter(start)) {
            throw ApiException.unprocessable("Request validation failed",
                    List.of(Map.of("field", field, "message", field.equals("end_date") ? "end_date must be after start_date" : "expiry_date must be after issue_date")));
        }
    }

    public VehicleInspection createInspection(InspectionCreate d, CurrentUser user) {
        vehicle(d.vehicleId(), true);
        Long inspector = d.inspectorEmployeeId() != null ? d.inspectorEmployeeId() : user.employeeId();
        if (inspector == null) throw ApiException.badRequest("INVALID_REQUEST", "inspector_employee_id is required for callers who are not employees");
        common.requireActiveEmployee(inspector, "Inspector");
        LocalDateTime when = d.inspectedAt() != null ? d.inspectedAt() : Clock.now();
        if (when.isAfter(Clock.now().plusMinutes(5))) throw ApiException.badRequest("INVALID_DATES", "inspected_at cannot be in the future");
        VehicleInspection i = new VehicleInspection();
        i.setVehicleId(d.vehicleId());
        i.setInspectorEmployeeId(inspector);
        i.setInspectedAt(when);
        i.setResult(d.result());
        i.setRemarks(d.remarks());
        db.save(i);
        audit.record("vehicle_inspections", i.getInspectionId(), "INSERT", null, m("vehicle_id", d.vehicleId(), "result", d.result(), "inspector", inspector), user.userId());
        db.flush();
        return i;
    }

    public FitnessCertificate createFitness(FitnessCreate d, CurrentUser user) {
        vehicle(d.vehicleId(), true);
        ordered(d.issueDate(), d.expiryDate(), "expiry_date");
        if (d.inspectionId() != null) {
            VehicleInspection ins = db.get(VehicleInspection.class, d.inspectionId(), "Inspection");
            if (!ins.getVehicleId().equals(d.vehicleId())) throw ApiException.conflict("INSPECTION_VEHICLE_MISMATCH", "The inspection belongs to a different vehicle");
            if (!"PASS".equals(ins.getResult())) throw ApiException.conflict("INSPECTION_NOT_PASSED", "A fitness certificate requires a PASSED inspection");
        }
        String status = !d.expiryDate().isBefore(Clock.today()) ? "ACTIVE" : "EXPIRED";
        FitnessCertificate c = new FitnessCertificate();
        c.setVehicleId(d.vehicleId());
        c.setInspectionId(d.inspectionId());
        c.setIssueDate(d.issueDate());
        c.setExpiryDate(d.expiryDate());
        c.setStatus(status);
        db.save(c);
        audit.record("fitness_certificates", c.getCertificateId(), "INSERT", null,
                m("vehicle_id", d.vehicleId(), "issue_date", d.issueDate(), "expiry_date", d.expiryDate(), "status", status), user.userId());
        db.flush();
        return c;
    }

    public FitnessCertificate revokeFitness(Long id, String reason, CurrentUser user) {
        FitnessCertificate c = db.lock(FitnessCertificate.class, id, "Fitness certificate");
        Common.checkTransition(Map.of("ACTIVE", Set.of("REVOKED", "EXPIRED"), "EXPIRED", Set.of(), "REVOKED", Set.of()), c.getStatus(), "REVOKED", "fitness certificate status");
        c.setStatus("REVOKED");
        audit.record("fitness_certificates", id, "UPDATE", m("status", "ACTIVE"), m("status", "REVOKED", "reason", reason), user.userId());
        db.flush();
        return c;
    }

    public PollutionCertificate createPollution(PollutionCreate d, CurrentUser user) {
        vehicle(d.vehicleId(), true);
        ordered(d.issueDate(), d.expiryDate(), "expiry_date");
        String status = !d.expiryDate().isBefore(Clock.today()) ? "ACTIVE" : "EXPIRED";
        PollutionCertificate c = new PollutionCertificate();
        c.setVehicleId(d.vehicleId());
        c.setIssueDate(d.issueDate());
        c.setExpiryDate(d.expiryDate());
        c.setStatus(status);
        db.save(c);
        audit.record("pollution_certificates", c.getPucId(), "INSERT", null, m("vehicle_id", d.vehicleId(), "expiry_date", d.expiryDate(), "status", status), user.userId());
        db.flush();
        return c;
    }

    public InsurancePolicy createInsurance(InsuranceCreate d, CurrentUser user) {
        vehicle(d.vehicleId(), true);
        ordered(d.startDate(), d.endDate(), "end_date");
        if (db.exists("select p.policyId from InsurancePolicy p where p.policyNumber = :n", "n", d.policyNumber())) {
            throw ApiException.conflict("DUPLICATE", "Insurance policy number already exists");
        }
        InsurancePolicy p = new InsurancePolicy();
        p.setVehicleId(d.vehicleId());
        p.setProviderName(d.providerName());
        p.setPolicyNumber(d.policyNumber());
        p.setStartDate(d.startDate());
        p.setEndDate(d.endDate());
        db.save(p);
        audit.record("insurance_policies", p.getPolicyId(), "INSERT", null, m("vehicle_id", d.vehicleId(), "policy_number", d.policyNumber(), "end_date", d.endDate()), user.userId());
        db.flush();
        return p;
    }

    public RoadTaxRecord createRoadTax(RoadTaxCreate d, CurrentUser user) {
        vehicle(d.vehicleId(), true);
        if (db.exists("select r.taxRecordId from RoadTaxRecord r where r.vehicleId = :v and r.assessmentYear = :y", "v", d.vehicleId(), "y", d.assessmentYear())) {
            throw ApiException.conflict("DUPLICATE", "A road-tax record already exists for this vehicle and assessment year");
        }
        String status = d.dueDate().isBefore(Clock.today()) ? "OVERDUE" : "DUE";
        RoadTaxRecord r = new RoadTaxRecord();
        r.setVehicleId(d.vehicleId());
        r.setAssessmentYear(d.assessmentYear());
        r.setAmountDue(Fees.money(d.amountDue()));
        r.setDueDate(d.dueDate());
        r.setStatus(status);
        db.save(r);
        audit.record("road_tax_records", r.getTaxRecordId(), "INSERT", null,
                m("vehicle_id", d.vehicleId(), "assessment_year", d.assessmentYear(), "amount_due", r.getAmountDue(), "status", status), user.userId());
        db.flush();
        return r;
    }

    /** Persist time-driven status changes: lapsed certificates -> EXPIRED, unpaid past-due road tax -> OVERDUE. */
    public Map<String, Object> refreshStatuses(CurrentUser user) {
        LocalDate t = Clock.today();
        Map<String, Object> out = m(
                "fitness_expired", db.update("update FitnessCertificate f set f.status = 'EXPIRED' where f.status = 'ACTIVE' and f.expiryDate < :t", "t", t),
                "pollution_expired", db.update("update PollutionCertificate f set f.status = 'EXPIRED' where f.status = 'ACTIVE' and f.expiryDate < :t", "t", t),
                "road_tax_overdue", db.update("update RoadTaxRecord r set r.status = 'OVERDUE' where r.status = 'DUE' and r.dueDate < :t", "t", t));
        if (out.values().stream().anyMatch(v -> (Integer) v > 0)) audit.record("compliance_refresh", 0L, "UPDATE", null, out, user.userId());
        db.flush();
        return out;
    }

    // ---- consolidated status / expiry queries ---------------------------------------------------------------

    private ComplianceItem item(boolean required, boolean valid, String detail, LocalDate expiry, Long recordId) {
        return new ComplianceItem(required, valid, detail, expiry, expiry == null ? null : (int) ChronoUnit.DAYS.between(Clock.today(), expiry), recordId);
    }

    @Transactional(readOnly = true)
    public ComplianceSummary summary(Long vehicleId) {
        Vehicle v = db.get(Vehicle.class, vehicleId, "Vehicle");
        LocalDate t = Clock.today();
        FitnessCertificate fit = db.first(FitnessCertificate.class, "select f from FitnessCertificate f where f.vehicleId = :v and f.status = 'ACTIVE' "
                + "and f.expiryDate >= :t order by f.expiryDate desc", "v", vehicleId, "t", t);
        boolean fitRequired = Boolean.TRUE.equals(db.get(VehicleType.class, v.getVehicleTypeId(), "Vehicle type").getIsCommercial());
        ComplianceItem fitness = fit != null ? item(fitRequired, true, "Valid fitness certificate", fit.getExpiryDate(), fit.getCertificateId())
                : item(fitRequired, !fitRequired, fitRequired ? "No valid fitness certificate" : "Not required for non-commercial vehicles", null, null);

        PollutionCertificate puc = db.first(PollutionCertificate.class, "select c from PollutionCertificate c where c.vehicleId = :v and c.status = 'ACTIVE' "
                + "and c.expiryDate >= :t order by c.expiryDate desc", "v", vehicleId, "t", t);
        ComplianceItem pollution = puc != null ? item(true, true, "Valid pollution certificate", puc.getExpiryDate(), puc.getPucId())
                : item(true, false, "No valid pollution certificate", null, null);

        InsurancePolicy ins = db.first(InsurancePolicy.class, "select p from InsurancePolicy p where p.vehicleId = :v and p.startDate <= :t "
                + "and p.endDate >= :t order by p.endDate desc", "v", vehicleId, "t", t);
        ComplianceItem insurance = ins != null ? item(true, true, "Insured", ins.getEndDate(), ins.getPolicyId())
                : item(true, false, "No active insurance policy", null, null);

        RoadTaxRecord overdue = db.first(RoadTaxRecord.class, "select r from RoadTaxRecord r where r.vehicleId = :v and r.status <> 'PAID' "
                + "and r.dueDate < :t order by r.dueDate", "v", vehicleId, "t", t);
        RoadTaxRecord due = db.first(RoadTaxRecord.class, "select r from RoadTaxRecord r where r.vehicleId = :v and r.status <> 'PAID' order by r.dueDate", "v", vehicleId);
        ComplianceItem roadTax = overdue != null
                ? item(true, false, "Road tax for " + overdue.getAssessmentYear() + " is overdue", overdue.getDueDate(), overdue.getTaxRecordId())
                : item(true, true, "No overdue road tax" + (due != null ? " (a payment is upcoming)" : ""),
                due == null ? null : due.getDueDate(), due == null ? null : due.getTaxRecordId());

        boolean ok = "ACTIVE".equals(v.getStatus()) && Arrays.asList(fitness, pollution, insurance, roadTax).stream().allMatch(i -> !i.required() || i.valid());
        return new ComplianceSummary(vehicleId, v.getRegistrationNumber(), t, ok ? "COMPLIANT" : "NON_COMPLIANT", fitness, pollution, insurance, roadTax);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpiringItem> expiring(PageParams p, int days, String kind) {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("FITNESS", "SELECT 'FITNESS' AS kind, f.certificate_id AS record_id, f.vehicle_id AS vehicle_id, v.registration_number AS registration_number, "
                + "f.expiry_date AS expiry_date, DATEDIFF(f.expiry_date, :today) AS days_left FROM fitness_certificates f JOIN vehicles v ON v.vehicle_id = f.vehicle_id "
                + "WHERE f.expiry_date BETWEEN :today AND :horizon AND f.status = 'ACTIVE'");
        parts.put("POLLUTION", "SELECT 'POLLUTION' AS kind, c.puc_id AS record_id, c.vehicle_id AS vehicle_id, v.registration_number AS registration_number, c.expiry_date AS expiry_date, DATEDIFF(c.expiry_date, :today) AS days_left "
                + "FROM pollution_certificates c JOIN vehicles v ON v.vehicle_id = c.vehicle_id WHERE c.expiry_date BETWEEN :today AND :horizon AND c.status = 'ACTIVE'");
        parts.put("INSURANCE", "SELECT 'INSURANCE' AS kind, i.policy_id AS record_id, i.vehicle_id AS vehicle_id, v.registration_number AS registration_number, i.end_date AS expiry_date, DATEDIFF(i.end_date, :today) AS days_left "
                + "FROM insurance_policies i JOIN vehicles v ON v.vehicle_id = i.vehicle_id WHERE i.end_date BETWEEN :today AND :horizon");
        if (kind != null && !parts.containsKey(kind)) throw ApiException.badRequest("INVALID_KIND", "kind must be one of FITNESS, POLLUTION, INSURANCE");
        String union = parts.entrySet().stream().filter(e -> kind == null || e.getKey().equals(kind)).map(Map.Entry::getValue)
                .reduce((a, b) -> a + " UNION ALL " + b).orElseThrow();
        Map<String, Object> params = new LinkedHashMap<>(Map.of("today", Clock.today(), "horizon", Clock.today().plusDays(days)));
        long total = ((Number) db.nativeScalar("SELECT COUNT(*) FROM (" + union + ") u", params)).longValue();
        List<Object[]> rows = db.nativeRows("SELECT * FROM (" + union + ") u ORDER BY u.expiry_date, u.kind, u.record_id LIMIT "
                + p.pageSize() + " OFFSET " + p.offset(), params);   // page numbers are validated ints, never user text
        List<ExpiringItem> items = rows.stream().map(r -> new ExpiringItem((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue(),
                (String) r[3], toLocalDate(r[4]), ((Number) r[5]).intValue())).toList();
        return PageResponse.of(items, p, total);
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString().substring(0, 10));
    }
}
