package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.SupportDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rto.core.Audit.m;

/** Complaints, appeals (polymorphic targets) and the read-only audit trail. */
@Service
@Transactional
public class SupportService {
    static final Map<String, Set<String>> COMPLAINT_TRANSITIONS = Map.of(
            "OPEN", Set.of("IN_PROGRESS", "CLOSED"),
            "IN_PROGRESS", Set.of("RESOLVED", "CLOSED"),
            "RESOLVED", Set.of("CLOSED", "IN_PROGRESS"),   // IN_PROGRESS = explicit re-open; any other backwards step is refused
            "CLOSED", Set.of());
    static final Map<String, Set<String>> APPEAL_TRANSITIONS = Map.of(
            "FILED", Set.of("UNDER_REVIEW"), "UNDER_REVIEW", Set.of("UPHELD", "DISMISSED"), "UPHELD", Set.of(), "DISMISSED", Set.of());

    private final Db db;
    private final Audit audit;
    private final Common common;
    private final ApplicationService apps;
    private final LicenceService licences;
    private final ViolationService violations;
    private final Notifications notifications;

    public SupportService(Db db, Audit audit, Common common, ApplicationService apps, LicenceService licences,
                          ViolationService violations, Notifications notifications) {
        this.db = db;
        this.audit = audit;
        this.common = common;
        this.apps = apps;
        this.licences = licences;
        this.violations = violations;
        this.notifications = notifications;
    }

    private Long filer(Long requested, CurrentUser user, String staffPerm) {
        if (user.has(staffPerm)) {
            if (requested == null) {
                if (user.citizenId() == null) throw ApiException.badRequest("INVALID_REQUEST", "citizen_id is required");
                return user.citizenId();
            }
            return requested;
        }
        if (user.citizenId() == null) throw ApiException.forbidden("NOT_A_CITIZEN", "Your account is not linked to a citizen record");
        if (requested != null && !requested.equals(user.citizenId())) throw ApiException.forbidden("PERMISSION_DENIED", "You can only file on your own behalf");
        return user.citizenId();
    }

    // ---- complaints --------------------------------------------------------------------------------------------

    public Complaint createComplaint(ComplaintCreate d, CurrentUser user) {
        Long citizenId = filer(d.citizenId(), user, "complaint.manage");
        db.get(Citizen.class, citizenId, "Citizen");
        common.requireActiveOffice(d.officeId());
        Complaint c = new Complaint();
        c.setCitizenId(citizenId);
        c.setOfficeId(d.officeId());
        c.setSubject(d.subject());
        c.setDescription(d.description());
        c.setStatus("OPEN");
        c.setFiledAt(Clock.now());
        db.save(c);
        audit.record("complaints", c.getComplaintId(), "INSERT", null, m("citizen_id", citizenId, "office_id", d.officeId(), "subject", d.subject(), "status", "OPEN"), user.userId());
        db.flush();
        return c;
    }

    @Transactional(readOnly = true)
    public Complaint complaint(CurrentUser user, Long id) {
        Complaint c = db.get(Complaint.class, id, "Complaint");
        if (user.has("complaint.manage") || (user.has("complaint.view_own") && user.isCitizen(c.getCitizenId()))) return c;
        throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this complaint");
    }

    @Transactional(readOnly = true)
    public PageResponse<Complaint> complaints(CurrentUser user, PageParams p, String status, Long officeId, Long citizenId) {
        QB q = new QB("c", "Complaint c");
        if (!user.has("complaint.manage")) {
            if (!user.has("complaint.view_own")) throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: complaint.manage");
            q.and("c.citizenId = :me", "me", user.citizenId() == null ? 0L : user.citizenId());
        }
        q.search(p.search(), "c.subject", "c.description").eq("c.status", status).eq("c.officeId", officeId).eq("c.citizenId", citizenId);
        return db.page(q, Complaint.class, p, Map.of("filed_at", "c.filedAt", "status", "c.status", "complaint_id", "c.complaintId"), "c.complaintId", true);
    }

    public Complaint changeComplaintStatus(Long id, String next, String note, CurrentUser user) {
        Complaint c = db.lock(Complaint.class, id, "Complaint");
        String prev = c.getStatus();
        Common.checkTransition(COMPLAINT_TRANSITIONS, prev, next, "complaint status");
        c.setStatus(next);   // filed_at is never touched
        audit.record("complaints", id, "UPDATE", m("status", prev), note == null ? m("status", next) : m("status", next, "note", note), user.userId());
        db.flush();
        Citizen citizen = db.get(Citizen.class, c.getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Complaint update", "Your complaint '" + c.getSubject() + "' is now " + next + ".");
        return c;
    }

    // ---- appeals -------------------------------------------------------------------------------------------------

    /** The polymorphic against_id has no FK: prove it exists, is appealable and concerns this citizen. */
    private void validateTarget(String type, Long id, Long citizenId) {
        switch (type) {
            case "CHALLAN" -> {
                Challan ch = db.find(Challan.class, id);
                if (ch == null) throw ApiException.conflict("INVALID_APPEAL_TARGET", "Challan " + id + " does not exist");
                if ("CANCELLED".equals(ch.getStatus())) throw ApiException.conflict("INVALID_APPEAL_TARGET", "The challan is already cancelled");
                boolean entitled = false;
                for (Object[] r : db.rows("select v.driverCitizenId, v.vehicleId, v.occurredAt from Violation v join ChallanViolation cv "
                        + "on cv.violationId = v.violationId where cv.challanId = :c", "c", id)) {
                    LocalDate day = ((java.time.LocalDateTime) r[2]).toLocalDate();
                    boolean owner = db.exists("select o.ownershipId from VehicleOwnership o where o.vehicleId = :v and o.effectiveFrom <= :d "
                            + "and (o.effectiveTo is null or o.effectiveTo >= :d) and o.citizenId = :c", "v", r[1], "d", day, "c", citizenId);
                    if (citizenId.equals(r[0]) || owner) entitled = true;
                }
                if (!entitled) throw ApiException.conflict("APPEAL_NOT_ENTITLED", "This challan does not concern the appellant");
            }
            case "APPLICATION_REJECTION" -> {
                Application a = db.find(Application.class, id);
                if (a == null) throw ApiException.conflict("INVALID_APPEAL_TARGET", "Application " + id + " does not exist");
                if (!"REJECTED".equals(a.getCurrentStatus())) throw ApiException.conflict("INVALID_APPEAL_TARGET", "The application is " + a.getCurrentStatus() + ", not REJECTED");
                if (!citizenId.equals(apps.applicantOf(a).getCitizenId())) throw ApiException.conflict("APPEAL_NOT_ENTITLED", "This application does not belong to the appellant");
            }
            default -> {
                DrivingLicence dl = db.find(DrivingLicence.class, id);
                if (dl == null) throw ApiException.conflict("INVALID_APPEAL_TARGET", "Driving licence " + id + " does not exist");
                if (!"SUSPENDED".equals(dl.getCurrentStatus())) throw ApiException.conflict("INVALID_APPEAL_TARGET", "The licence is " + dl.getCurrentStatus() + ", not SUSPENDED");
                if (!dl.getCitizenId().equals(citizenId)) throw ApiException.conflict("APPEAL_NOT_ENTITLED", "This licence does not belong to the appellant");
            }
        }
    }

    public Appeal createAppeal(AppealCreate d, CurrentUser user) {
        Long citizenId = filer(d.citizenId(), user, "appeal.review");
        db.get(Citizen.class, citizenId, "Citizen");
        validateTarget(d.againstType(), d.againstId(), citizenId);
        if (db.exists("select a.appealId from Appeal a where a.againstType = :t and a.againstId = :i and a.status in ('FILED','UNDER_REVIEW')",
                "t", d.againstType(), "i", d.againstId())) {
            throw ApiException.conflict("APPEAL_EXISTS", "An appeal against this item is already open");
        }
        Appeal a = new Appeal();
        a.setCitizenId(citizenId);
        a.setAgainstType(d.againstType());
        a.setAgainstId(d.againstId());
        a.setGrounds(d.grounds());
        a.setStatus("FILED");
        a.setFiledAt(Clock.now());
        db.save(a);
        audit.record("appeals", a.getAppealId(), "INSERT", null, m("citizen_id", citizenId, "against_type", d.againstType(), "against_id", d.againstId()), user.userId());
        db.flush();
        return a;
    }

    @Transactional(readOnly = true)
    public Appeal appeal(CurrentUser user, Long id) {
        Appeal a = db.get(Appeal.class, id, "Appeal");
        if (user.has("appeal.review") || (user.has("appeal.view_own") && user.isCitizen(a.getCitizenId()))) return a;
        throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this appeal");
    }

    @Transactional(readOnly = true)
    public PageResponse<Appeal> appeals(CurrentUser user, PageParams p, String status, String againstType, Long citizenId) {
        QB q = new QB("a", "Appeal a");
        if (!user.has("appeal.review")) {
            if (!user.has("appeal.view_own")) throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: appeal.review");
            q.and("a.citizenId = :me", "me", user.citizenId() == null ? 0L : user.citizenId());
        }
        q.eq("a.status", status).eq("a.againstType", againstType).eq("a.citizenId", citizenId).search(p.search(), "a.grounds");
        return db.page(q, Appeal.class, p, Map.of("filed_at", "a.filedAt", "status", "a.status", "appeal_id", "a.appealId"), "a.appealId", true);
    }

    public Appeal startReview(Long id, CurrentUser user) {
        Appeal a = db.lock(Appeal.class, id, "Appeal");
        Common.checkTransition(APPEAL_TRANSITIONS, a.getStatus(), "UNDER_REVIEW", "appeal status");
        a.setStatus("UNDER_REVIEW");
        audit.record("appeals", id, "UPDATE", m("status", "FILED"), m("status", "UNDER_REVIEW", "reviewer_user_id", user.userId()), user.userId());
        db.flush();
        return a;
    }

    /** UPHELD / DISMISSED. The appeal row, its effect on the target and the audit trail commit together. */
    public Appeal decide(Long id, String outcome, String note, CurrentUser user) {
        Appeal a = db.lock(Appeal.class, id, "Appeal");
        if (Set.of("UPHELD", "DISMISSED").contains(a.getStatus())) {
            throw ApiException.conflict("APPEAL_ALREADY_RESOLVED", "This appeal was already decided (" + a.getStatus() + ")");
        }
        Common.checkTransition(APPEAL_TRANSITIONS, a.getStatus(), outcome, "appeal status");
        String effect = applyEffect(a, outcome, note, user);
        String prev = a.getStatus();
        a.setStatus(outcome);
        a.setResolvedAt(Clock.now());
        audit.record("appeals", id, "UPDATE", m("status", prev), m("status", outcome, "note", note, "decided_by_user_id", user.userId(), "effect", effect), user.userId());
        db.flush();
        Citizen citizen = db.get(Citizen.class, a.getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Appeal decision", "Your appeal #" + a.getAppealId() + " was " + outcome + ".");
        return a;
    }

    private String applyEffect(Appeal a, String outcome, String note, CurrentUser user) {
        boolean upheld = "UPHELD".equals(outcome);
        switch (a.getAgainstType()) {
            case "CHALLAN" -> {
                Challan ch = db.lock(Challan.class, a.getAgainstId(), "Challan");
                if (upheld) {
                    if (Set.of("ISSUED", "DISPUTED").contains(ch.getStatus())) {
                        violations.applyStatus(ch, "CANCELLED", user.userId(), "Appeal upheld");
                        return "challan cancelled";
                    }
                    return "challan left " + ch.getStatus() + " (refund the payment if one was made)";
                }
                if ("DISPUTED".equals(ch.getStatus())) {
                    violations.applyStatus(ch, "ISSUED", user.userId(), "Appeal dismissed");
                    return "challan returned to ISSUED";
                }
                return "challan unchanged";
            }
            case "APPLICATION_REJECTION" -> {
                Application app = apps.get(a.getAgainstId(), true);
                if (upheld) {
                    apps.reopenAfterAppeal(app, user.userId(), note);
                    return "application reopened at UNDER_VERIFICATION";
                }
                return "rejection stands";
            }
            default -> {
                DrivingLicence dl = licences.getLicence(a.getAgainstId(), true);
                if (upheld) {
                    licences.applyLicenceStatus(dl, "ACTIVE", "Appeal upheld" + (note != null && !note.isBlank() ? ": " + note : ""), user.userId(), null);
                    return "licence reinstated";
                }
                return "suspension stands";
            }
        }
    }

    // ---- audit trail (read-only) -----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AuditLog> audit(PageParams p, String table, Long recordId, String action, Long userId, LocalDate from, LocalDate to) {
        QB q = new QB("a", "AuditLog a").eq("a.tableName", table).eq("a.recordId", recordId).eq("a.action", action).eq("a.changedByUserId", userId);
        if (from != null) q.op("a.changedAt", ">=", from.atStartOfDay());
        if (to != null) q.op("a.changedAt", "<", to.plusDays(1).atStartOfDay());
        return db.page(q, AuditLog.class, p, Map.of("changed_at", "a.changedAt", "audit_id", "a.auditId"), "a.auditId", true);
    }
}
