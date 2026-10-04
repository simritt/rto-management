package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.ViolationDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.rto.core.Audit.m;

/** Violations (attached to a VEHICLE; the driver may be unknown) and challans bundling them. */
@Service
@Transactional
public class ViolationService {
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "ISSUED", Set.of("DISPUTED", "PAID", "CANCELLED"),
            "DISPUTED", Set.of("ISSUED", "CANCELLED"),   // back to ISSUED when the dispute is dismissed
            "PAID", Set.of(), "CANCELLED", Set.of());

    private final Db db;
    private final Audit audit;
    private final Common common;
    private final Notifications notifications;

    public ViolationService(Db db, Audit audit, Common common, Notifications notifications) {
        this.db = db;
        this.audit = audit;
        this.common = common;
        this.notifications = notifications;
    }

    // ---- violations ---------------------------------------------------------------------------------------

    private Long liveChallanId(Long violationId) {
        return db.first(Long.class, "select cv.challanId from ChallanViolation cv join Challan c on c.challanId = cv.challanId "
                + "where cv.violationId = :v and c.status <> 'CANCELLED'", "v", violationId);
    }

    public ViolationOut out(Violation v) {
        ViolationType t = db.get(ViolationType.class, v.getViolationTypeId(), "Violation type");
        return new ViolationOut(v.getViolationId(), v.getVehicleId(), db.get(Vehicle.class, v.getVehicleId(), "Vehicle").getRegistrationNumber(),
                v.getDriverCitizenId(), v.getViolationTypeId(), t.getDescription(), t.getBaseFineAmount(), v.getOfficerEmployeeId(),
                v.getLocation(), v.getOccurredAt(), liveChallanId(v.getViolationId()));
    }

    public ViolationOut create(ViolationCreate d, CurrentUser user) {
        db.get(Vehicle.class, d.vehicleId(), "Vehicle");
        db.get(ViolationType.class, d.violationTypeId(), "Violation type");
        if (d.driverCitizenId() != null) db.get(Citizen.class, d.driverCitizenId(), "Driver citizen");   // the driver is optional
        Long officer = d.officerEmployeeId() != null ? d.officerEmployeeId() : user.employeeId();
        if (officer == null) throw ApiException.badRequest("INVALID_REQUEST", "officer_employee_id is required for callers who are not employees");
        common.requireActiveEmployee(officer, "Officer");
        if (d.occurredAt().isAfter(Clock.now().plusMinutes(5))) throw ApiException.badRequest("INVALID_DATES", "occurred_at cannot be in the future");
        Violation v = new Violation();
        v.setVehicleId(d.vehicleId());
        v.setDriverCitizenId(d.driverCitizenId());
        v.setViolationTypeId(d.violationTypeId());
        v.setOfficerEmployeeId(officer);
        v.setLocation(d.location());
        v.setOccurredAt(d.occurredAt());
        db.save(v);
        audit.record("violations", v.getViolationId(), "INSERT", null, m("vehicle_id", d.vehicleId(), "driver_citizen_id", d.driverCitizenId(),
                "violation_type_id", d.violationTypeId(), "occurred_at", d.occurredAt()), user.userId());
        db.flush();
        return out(v);
    }

    @Transactional(readOnly = true)
    public ViolationOut view(Long id) {
        return out(db.get(Violation.class, id, "Violation"));
    }

    @Transactional(readOnly = true)
    public PageResponse<ViolationOut> list(PageParams p, Long vehicleId, Long driverCitizenId, Long violationTypeId, Long officerId,
                                           LocalDate from, LocalDate to, Boolean unchallaned) {
        QB q = new QB("v", "Violation v join Vehicle ve on ve.vehicleId = v.vehicleId").select("v");
        if (p.search() != null) {
            q.and("(v.location like :s escape '\\' or ve.registrationNumber like :su escape '\\')", "s", QB.like(p.search()), "su", QB.like(p.search().toUpperCase()));
        }
        q.eq("v.vehicleId", vehicleId).eq("v.driverCitizenId", driverCitizenId).eq("v.violationTypeId", violationTypeId).eq("v.officerEmployeeId", officerId);
        if (from != null) q.op("v.occurredAt", ">=", from.atStartOfDay());
        if (to != null) q.op("v.occurredAt", "<", to.plusDays(1).atStartOfDay());
        if (unchallaned != null) {
            String live = "select cv.violationId from ChallanViolation cv join Challan c on c.challanId = cv.challanId where c.status <> 'CANCELLED'";
            q.and("v.violationId " + (unchallaned ? "not in" : "in") + " (" + live + ")");
        }
        return db.page(q, Violation.class, p, Map.of("occurred_at", "v.occurredAt", "violation_id", "v.violationId"), "v.violationId", true).map(this::out);
    }

    // ---- challans -----------------------------------------------------------------------------------------

    private void history(Long challanId, String prev, String next) {
        ChallanStatusHistory h = new ChallanStatusHistory();
        h.setChallanId(challanId);
        h.setPreviousStatus(prev);
        h.setNewStatus(next);
        db.save(h);
    }

    /** Always derived from the violation types' base fines at the database (never trusted from clients). */
    public BigDecimal total(Long challanId) {
        BigDecimal t = db.first(BigDecimal.class, "select coalesce(sum(vt.baseFineAmount), 0) from ChallanViolation cv "
                + "join Violation v on v.violationId = cv.violationId join ViolationType vt on vt.violationTypeId = v.violationTypeId "
                + "where cv.challanId = :c", "c", challanId);
        return Fees.money(t == null ? BigDecimal.ZERO : t);
    }

    private List<Violation> loadViolations(List<Long> ids, Long sameVehicleAs) {
        List<Long> unique = ids.stream().distinct().sorted().toList();
        if (unique.size() != ids.size()) throw ApiException.badRequest("DUPLICATE_VIOLATION", "violation_ids contains duplicates");
        List<Violation> rows = db.lockList(Violation.class, "select v from Violation v where v.violationId in :ids", "ids", unique);
        Set<Long> found = new HashSet<>();
        rows.forEach(r -> found.add(r.getViolationId()));
        List<Long> missing = unique.stream().filter(i -> !found.contains(i)).toList();
        if (!missing.isEmpty()) throw ApiException.badRequest("INVALID_REFERENCE", "Violation(s) not found: " + missing);
        Set<Long> vehicles = new HashSet<>();
        rows.forEach(r -> vehicles.add(r.getVehicleId()));
        if (sameVehicleAs != null) vehicles.add(sameVehicleAs);
        if (vehicles.size() > 1) throw ApiException.conflict("MIXED_VEHICLES", "All violations on a challan must concern the same vehicle");
        List<Long> taken = rows.stream().map(Violation::getViolationId).filter(i -> liveChallanId(i) != null).sorted().toList();
        if (!taken.isEmpty()) throw ApiException.conflict("VIOLATION_ALREADY_CHALLANED", "Violation(s) already on a live challan: " + taken);
        return rows;
    }

    private Long challanVehicle(Long challanId) {
        return db.first(Long.class, "select v.vehicleId from Violation v join ChallanViolation cv on cv.violationId = v.violationId where cv.challanId = :c", "c", challanId);
    }

    private void link(Long challanId, Long violationId) {
        ChallanViolation cv = new ChallanViolation();
        cv.setChallanId(challanId);
        cv.setViolationId(violationId);
        db.save(cv);
    }

    public ChallanDetail create(ChallanCreate d, CurrentUser user) {
        List<Violation> violations = loadViolations(d.violationIds(), null);
        String number = d.challanNumber() != null ? d.challanNumber() : Common.genNumber("CHN");
        if (db.exists("select c.challanId from Challan c where c.challanNumber = :n", "n", number)) throw ApiException.conflict("DUPLICATE", "Challan number already exists");
        Challan c = new Challan();
        c.setChallanNumber(number);
        c.setTotalAmount(Fees.ZERO);
        c.setStatus("ISSUED");
        c.setIssuedAt(Clock.now());
        db.save(c);
        for (Violation v : violations) link(c.getChallanId(), v.getViolationId());
        db.flush();
        c.setTotalAmount(total(c.getChallanId()));
        history(c.getChallanId(), null, "ISSUED");
        audit.record("challans", c.getChallanId(), "INSERT", null, m("challan_number", number,
                "violation_ids", d.violationIds().stream().sorted().toList(), "total_amount", c.getTotalAmount(), "status", "ISSUED"), user.userId());
        db.flush();
        notifyParty(violations.get(0), "Challan issued", "Challan " + number + " for " + c.getTotalAmount().toPlainString()
                + " has been issued against your vehicle/licence.");
        return detail(c);
    }

    private void notifyParty(Violation v, String subject, String message) {
        Long citizenId = v.getDriverCitizenId() != null ? v.getDriverCitizenId()
                : db.first(Long.class, "select o.citizenId from VehicleOwnership o where o.vehicleId = :v and o.effectiveTo is null", "v", v.getVehicleId());
        if (citizenId != null) notifications.notify(db.get(Citizen.class, citizenId, "Citizen").getPersonId(), subject, message);
    }

    private boolean hasPayment(Long challanId, List<String> statuses) {
        return db.exists("select p.paymentId from Payment p join PayableType t on t.payableTypeId = p.payableTypeId "
                + "where t.typeName = 'CHALLAN' and p.payableId = :c and p.status in :st", "c", challanId, "st", statuses);
    }

    public ChallanDetail addViolations(Long challanId, List<Long> ids, CurrentUser user) {
        Challan c = db.lock(Challan.class, challanId, "Challan");
        if (!"ISSUED".equals(c.getStatus())) {
            throw ApiException.conflict("INVALID_STATE", "Violations can only be added to an ISSUED challan (this one is " + c.getStatus() + ")");
        }
        Set<Long> already = new HashSet<>(db.list(Long.class, "select cv.violationId from ChallanViolation cv where cv.challanId = :c", "c", challanId));
        List<Long> dup = ids.stream().filter(already::contains).sorted().toList();
        if (!dup.isEmpty()) throw ApiException.conflict("DUPLICATE_VIOLATION", "Violation(s) already on this challan: " + dup);
        if (hasPayment(challanId, List.of("PENDING", "SUCCESS"))) {
            throw ApiException.conflict("PAYMENT_IN_PROGRESS", "A payment exists for this challan; its amount can no longer change");
        }
        List<Violation> violations = loadViolations(ids, challanVehicle(challanId));
        for (Violation v : violations) link(challanId, v.getViolationId());
        db.flush();
        BigDecimal old = c.getTotalAmount();
        c.setTotalAmount(total(challanId));
        audit.record("challans", challanId, "UPDATE", m("total_amount", old),
                m("total_amount", c.getTotalAmount(), "added_violation_ids", ids.stream().sorted().toList()), user.userId());
        db.flush();
        return detail(c);
    }

    /** Single entry point for every challan status change (history + audit); the caller owns the transaction. */
    public String applyStatus(Challan c, String next, Long userId, String reason) {
        String prev = c.getStatus();
        Common.checkTransition(TRANSITIONS, prev, next, "challan status");
        c.setStatus(next);
        history(c.getChallanId(), prev, next);
        audit.record("challans", c.getChallanId(), "UPDATE", m("status", prev), reason == null ? m("status", next) : m("status", next, "reason", reason), userId);
        return prev;
    }

    public ChallanDetail changeStatus(Long id, String next, String reason, CurrentUser user) {
        Challan c = db.lock(Challan.class, id, "Challan");
        if ("CANCELLED".equals(next) && hasPayment(id, List.of("SUCCESS"))) {
            throw ApiException.conflict("ALREADY_PAID", "A paid challan cannot be cancelled; refund the payment instead");
        }
        applyStatus(c, next, user.userId(), reason);
        db.flush();
        Violation first = db.first(Violation.class, "select v from Violation v join ChallanViolation cv on cv.violationId = v.violationId where cv.challanId = :c", "c", id);
        if (first != null) notifyParty(first, "Challan " + next.charAt(0) + next.substring(1).toLowerCase(), "Challan " + c.getChallanNumber() + " is now " + next + ".");
        return detail(c);
    }

    public ChallanDetail detail(Challan c) {
        List<Violation> vs = db.list(Violation.class, "select v from Violation v join ChallanViolation cv on cv.violationId = v.violationId "
                + "where cv.challanId = :c order by v.violationId", "c", c.getChallanId());
        List<ViolationOut> outs = vs.stream().map(this::out).toList();
        return new ChallanDetail(c.getChallanId(), c.getChallanNumber(), c.getTotalAmount(), c.getStatus(), c.getIssuedAt(),
                vs.isEmpty() ? null : vs.get(0).getVehicleId(), outs.isEmpty() ? null : outs.get(0).registrationNumber(), outs);
    }

    @Transactional(readOnly = true)
    public ChallanDetail viewChallan(Long id) {
        return detail(db.get(Challan.class, id, "Challan"));
    }

    @Transactional(readOnly = true)
    public PageResponse<Challan> list(PageParams p, String status, Long vehicleId, Long driverCitizenId, LocalDate from, LocalDate to) {
        QB q = new QB("c", "Challan c").search(p.search(), "c.challanNumber").eq("c.status", status);
        if (vehicleId != null) {
            q.and("c.challanId in (select cv.challanId from ChallanViolation cv join Violation v on v.violationId = cv.violationId where v.vehicleId = :ve)", "ve", vehicleId);
        }
        if (driverCitizenId != null) {
            q.and("c.challanId in (select cv.challanId from ChallanViolation cv join Violation v on v.violationId = cv.violationId where v.driverCitizenId = :dr)", "dr", driverCitizenId);
        }
        if (from != null) q.op("c.issuedAt", ">=", from.atStartOfDay());
        if (to != null) q.op("c.issuedAt", "<", to.plusDays(1).atStartOfDay());
        return db.page(q, Challan.class, p, Map.of("issued_at", "c.issuedAt", "total_amount", "c.totalAmount", "challan_number", "c.challanNumber",
                "challan_id", "c.challanId"), "c.challanId", true);
    }

    @Transactional(readOnly = true)
    public List<ChallanStatusHistory> history(Long id) {
        db.get(Challan.class, id, "Challan");
        return db.list(ChallanStatusHistory.class, "select h from ChallanStatusHistory h where h.challanId = :c order by h.historyId", "c", id);
    }
}
