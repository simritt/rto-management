package com.rto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.ApplicationDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static com.rto.core.Audit.m;

/** Generic application workflow engine: creation, assignment and the validated status state machine. */
@Service
@Transactional
public class ApplicationService {

    /** Every legal move of the workflow. Anything absent is a 409. */
    public static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "SUBMITTED", Set.of("DOCS_PENDING", "UNDER_VERIFICATION", "REJECTED", "CANCELLED"),
            "DOCS_PENDING", Set.of("UNDER_VERIFICATION", "REJECTED", "CANCELLED"),
            "UNDER_VERIFICATION", Set.of("DOCS_PENDING", "APPOINTMENT_SCHEDULED", "AWAITING_PAYMENT", "REJECTED", "CANCELLED"),
            "APPOINTMENT_SCHEDULED", Set.of("UNDER_VERIFICATION", "AWAITING_PAYMENT", "REJECTED", "CANCELLED"),
            "AWAITING_PAYMENT", Set.of("APPROVED", "REJECTED", "CANCELLED"),
            "APPROVED", Set.of("COMPLETED"),
            "REJECTED", Set.of(), "COMPLETED", Set.of(), "CANCELLED", Set.of());
    public static final Set<String> TERMINAL = Set.of("REJECTED", "COMPLETED", "CANCELLED");
    static final Map<String, String> PERMISSION_FOR_TARGET = Map.of(
            "DOCS_PENDING", "application.verify", "UNDER_VERIFICATION", "application.verify",
            "APPOINTMENT_SCHEDULED", "application.verify", "AWAITING_PAYMENT", "application.verify",
            "APPROVED", "application.approve", "COMPLETED", "application.approve",
            "REJECTED", "application.reject", "CANCELLED", "application.cancel");
    static final Set<String> REASON_REQUIRED = Set.of("REJECTED", "CANCELLED", "DOCS_PENDING");
    public static final List<String> ACTIVE_APPOINTMENT = List.of("BOOKED", "RESCHEDULED");

    private final Db db;
    private final Audit audit;
    private final Common common;
    private final Fees fees;
    private final Notifications notifications;
    private final Patch patch;

    public ApplicationService(Db db, Audit audit, Common common, Fees fees, Notifications notifications, Patch patch) {
        this.db = db;
        this.audit = audit;
        this.common = common;
        this.fees = fees;
        this.notifications = notifications;
        this.patch = patch;
    }

    // ---- access ---------------------------------------------------------------------------------------

    public Application get(Long id, boolean lock) {
        return lock ? db.lock(Application.class, id, "Application") : db.get(Application.class, id, "Application");
    }

    Applicant applicantOf(Application a) {
        return db.get(Applicant.class, a.getApplicantId(), "Applicant");
    }

    public boolean isOwner(CurrentUser user, Application a) {
        return user.citizenId() != null && user.citizenId().equals(applicantOf(a).getCitizenId());
    }

    public void assertCanView(CurrentUser user, Application a) {
        if (user.has("application.view") || (user.has("application.view_own") && isOwner(user, a))) return;
        throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this application");
    }

    // ---- representation ---------------------------------------------------------------------------------

    ApplicationOut out(Application a, Citizen c, Person p, ServiceType st) {
        return new ApplicationOut(a.getApplicationId(), a.getApplicationNumber(), c.getCitizenId(), c.getCitizenCode(),
                p.getFirstName() + " " + p.getLastName(), a.getServiceTypeId(), st.getServiceCode(), st.getServiceName(),
                a.getOfficeId(), a.getAssignedOfficerId(), a.getCurrentStatus(), a.getSubmittedAt(), a.getCompletedAt(), a.getRemarks());
    }

    public ApplicationOut out(Application a) {
        Citizen c = db.get(Citizen.class, applicantOf(a).getCitizenId(), "Citizen");
        return out(a, c, db.get(Person.class, c.getPersonId(), "Person"), db.get(ServiceType.class, a.getServiceTypeId(), "Service type"));
    }

    public ApplicationDetail detail(Application a) {
        ApplicationOut o = out(a);
        ServiceType st = db.get(ServiceType.class, a.getServiceTypeId(), "Service type");
        Appointment appt = db.first(Appointment.class, "select x from Appointment x where x.applicationId = :a", "a", a.getApplicationId());
        Map<String, Object> apptMap = appt == null ? null : m("appointment_id", appt.getAppointmentId(), "slot_id", appt.getSlotId(),
                "token_number", appt.getTokenNumber(), "status", appt.getStatus());
        return new ApplicationDetail(o.applicationId(), o.applicationNumber(), o.citizenId(), o.citizenCode(), o.citizenName(),
                o.serviceTypeId(), o.serviceCode(), o.serviceName(), o.officeId(), o.assignedOfficerId(), o.currentStatus(),
                o.submittedAt(), o.completedAt(), o.remarks(), fees.applicationFee(st, a.getSubmittedAt().toLocalDate()),
                st.getSlaDays(), db.count("select count(d) from Document d where d.applicationId = :a", "a", a.getApplicationId()),
                apptMap, TRANSITIONS.get(a.getCurrentStatus()).stream().sorted().toList());
    }

    private void history(Long applicationId, String prev, String next, Long userId, String reason) {
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplicationId(applicationId);
        h.setPreviousStatus(prev);
        h.setNewStatus(next);
        h.setChangedByUserId(userId);
        h.setReason(reason);
        db.save(h);
    }

    // ---- create / read / update -------------------------------------------------------------------------

    public ApplicationDetail create(ApplicationCreate d, CurrentUser user) {
        Long citizenId;
        if (user.has("application.create")) {
            citizenId = d.citizenId();
            if (citizenId == null) throw ApiException.badRequest("INVALID_REQUEST", "citizen_id is required");
        } else if (user.has("application.create_own")) {
            if (user.citizenId() == null) throw ApiException.forbidden("NOT_A_CITIZEN", "Your account is not linked to a citizen record");
            if (d.citizenId() != null && !d.citizenId().equals(user.citizenId())) {
                throw ApiException.forbidden("PERMISSION_DENIED", "You can only apply on your own behalf");
            }
            citizenId = user.citizenId();
        } else {
            throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: application.create");
        }
        Citizen citizen = db.lock(Citizen.class, citizenId, "Citizen");   // serialises applicant get-or-create
        if (Boolean.TRUE.equals(citizen.getBlacklisted())) {
            throw ApiException.conflict("CITIZEN_BLACKLISTED", "Blacklisted citizens cannot submit applications");
        }
        ServiceType service = db.get(ServiceType.class, d.serviceTypeId(), "Service type");
        common.requireActiveOffice(d.officeId());
        String number = d.applicationNumber() != null ? d.applicationNumber() : Common.genNumber("APP");
        if (db.exists("select a.applicationId from Application a where a.applicationNumber = :n", "n", number)) {
            throw ApiException.conflict("DUPLICATE", "Application number already exists");
        }
        Applicant applicant = db.first(Applicant.class, "select x from Applicant x where x.citizenId = :c", "c", citizenId);
        if (applicant == null) {
            applicant = new Applicant();
            applicant.setCitizenId(citizenId);
            applicant.setPreferredOfficeId(d.officeId());
            db.save(applicant);
        }
        Application app = new Application();
        app.setApplicationNumber(number);
        app.setApplicantId(applicant.getApplicantId());
        app.setServiceTypeId(service.getServiceTypeId());
        app.setOfficeId(d.officeId());
        app.setCurrentStatus("SUBMITTED");
        app.setRemarks(d.remarks());
        app.setSubmittedAt(Clock.now());
        db.save(app);
        history(app.getApplicationId(), null, "SUBMITTED", user.userId(), "Application submitted");
        audit.record("applications", app.getApplicationId(), "INSERT", null,
                m("application_number", number, "citizen_id", citizenId, "service_type_id", service.getServiceTypeId(),
                        "office_id", d.officeId(), "current_status", "SUBMITTED"), user.userId());
        db.flush();
        notifications.notify(citizen.getPersonId(), "Application submitted",
                "Your application " + number + " for " + service.getServiceName() + " has been submitted.");
        return detail(app);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationOut> list(CurrentUser user, PageParams p, String status, Long serviceTypeId, Long officeId,
                                             Long citizenId, Long assignedOfficerId, LocalDate from, LocalDate to) {
        QB q = new QB("a", "Application a join Applicant ap on ap.applicantId = a.applicantId "
                + "join Citizen c on c.citizenId = ap.citizenId join Person p on p.personId = c.personId "
                + "join ServiceType st on st.serviceTypeId = a.serviceTypeId").select("a, c, p, st");
        if (!user.has("application.view")) {
            if (!user.has("application.view_own")) {
                throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: application.view");
            }
            q.and("ap.citizenId = :me", "me", user.citizenId() == null ? 0L : user.citizenId());
        }
        q.search(p.search(), "a.applicationNumber", "c.citizenCode", "p.firstName", "p.lastName");
        q.eq("a.currentStatus", status).eq("a.serviceTypeId", serviceTypeId).eq("a.officeId", officeId)
                .eq("ap.citizenId", citizenId).eq("a.assignedOfficerId", assignedOfficerId);
        if (from != null) q.op("a.submittedAt", ">=", from.atStartOfDay());
        if (to != null) q.op("a.submittedAt", "<", to.plusDays(1).atStartOfDay());
        return db.page(q, Object[].class, p, Map.of("submitted_at", "a.submittedAt", "application_number", "a.applicationNumber",
                "status", "a.currentStatus", "application_id", "a.applicationId"), "a.applicationId", true)
                .map(r -> out((Application) r[0], (Citizen) r[1], (Person) r[2], (ServiceType) r[3]));
    }

    @Transactional(readOnly = true)
    public ApplicationDetail view(CurrentUser user, Long id) {
        Application a = get(id, false);
        assertCanView(user, a);
        return detail(a);
    }

    public ApplicationDetail update(Long id, JsonNode body, CurrentUser user) {
        Patch.Parsed<ApplicationUpdate> pr = patch.parse(body, ApplicationUpdate.class);
        Application a = get(id, true);
        if (TERMINAL.contains(a.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_CLOSED", "A " + a.getCurrentStatus() + " application can no longer be modified");
        }
        Map<String, Object> old = m(), neu = m();
        if (pr.has("remarks") && !Objects.equals(pr.dto().remarks(), a.getRemarks())) {
            old.put("remarks", a.getRemarks());
            neu.put("remarks", pr.dto().remarks());
            a.setRemarks(pr.dto().remarks());
        }
        Long office = pr.dto().officeId();
        if (office != null && !office.equals(a.getOfficeId())) {
            if (!Set.of("SUBMITTED", "DOCS_PENDING", "UNDER_VERIFICATION").contains(a.getCurrentStatus())) {
                throw ApiException.conflict("INVALID_STATE", "The office can only be changed before an appointment/payment stage");
            }
            common.requireActiveOffice(office);
            old.put("office_id", a.getOfficeId());
            neu.put("office_id", office);
            a.setOfficeId(office);
            if (a.getAssignedOfficerId() != null) {   // the officer belonged to the previous office
                old.put("assigned_officer_id", a.getAssignedOfficerId());
                neu.put("assigned_officer_id", null);
                a.setAssignedOfficerId(null);
            }
        }
        if (!neu.isEmpty()) audit.record("applications", id, "UPDATE", old, neu, user.userId());
        db.flush();
        return detail(a);
    }

    // ---- assignment ---------------------------------------------------------------------------------------

    public ApplicationDetail assign(Long id, Long officerId, CurrentUser user) {
        Application a = get(id, true);
        if (TERMINAL.contains(a.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_CLOSED", "A " + a.getCurrentStatus() + " application cannot be reassigned");
        }
        if (officerId != null) {
            Employee officer = db.get(Employee.class, officerId, "Officer");
            if (!Boolean.TRUE.equals(officer.getIsActive())) throw ApiException.conflict("EMPLOYEE_INACTIVE", "Officer is inactive");
            if (!db.exists("select p.postingId from EmployeePosting p where p.employeeId = :e and p.postedTo is null and p.officeId = :o",
                    "e", officerId, "o", a.getOfficeId())) {
                throw ApiException.conflict("OFFICER_NOT_AT_OFFICE", "Officer is not currently posted at this application's office");
            }
        }
        Long old = a.getAssignedOfficerId();
        if (!Objects.equals(old, officerId)) {
            a.setAssignedOfficerId(officerId);
            audit.record("applications", id, "UPDATE", m("assigned_officer_id", old), m("assigned_officer_id", officerId), user.userId());
        }
        db.flush();
        return detail(a);
    }

    // ---- status workflow ----------------------------------------------------------------------------------

    private void docsReady(Long applicationId) {
        List<Document> docs = db.list(Document.class, "select d from Document d where d.applicationId = :a", "a", applicationId);
        if (docs.isEmpty()) throw ApiException.conflict("DOCUMENTS_NOT_VERIFIED", "At least one verified document is required before this step");
        Map<Long, DocumentType> types = new HashMap<>();
        List<String> bad = new ArrayList<>();
        for (Document d : DocumentRules.latestPerType(docs).values()) {
            DocumentType t = types.computeIfAbsent(d.getDocumentTypeId(), k -> db.get(DocumentType.class, k, "Document type"));
            String eff = DocumentRules.effectiveStatus(d, t);
            if (!"VERIFIED".equals(eff)) bad.add(t.getTypeName() + " (" + eff + ")");
        }
        if (!bad.isEmpty()) {
            Collections.sort(bad);
            throw ApiException.conflict("DOCUMENTS_NOT_VERIFIED", "Documents are not verified: " + String.join(", ", bad));
        }
    }

    public boolean hasSuccessfulPayment(Long applicationId) {
        return db.exists("select p.paymentId from Payment p join PayableType t on t.payableTypeId = p.payableTypeId "
                + "where t.typeName = 'APPLICATION' and p.payableId = :id and p.status = 'SUCCESS'", "id", applicationId);
    }

    /** Frees the seat held by the application's active appointment (caller owns the transaction). */
    public boolean releaseActiveAppointment(Long applicationId) {
        Appointment appt = db.lockFirst(Appointment.class, "select x from Appointment x where x.applicationId = :a and x.status in :st",
                "a", applicationId, "st", ACTIVE_APPOINTMENT);
        if (appt == null) return false;
        releaseSeat(appt.getSlotId());
        appt.setStatus("CANCELLED");
        return true;
    }

    public void releaseSeat(Long slotId) {
        db.update("update AppointmentSlot s set s.bookedCount = s.bookedCount - 1 where s.slotId = :id and s.bookedCount > 0", "id", slotId);
        AppointmentSlot slot = db.find(AppointmentSlot.class, slotId);
        if (slot != null) db.refresh(slot);
    }

    /** Core mutation used by every path that moves an application (caller commits). Returns the previous status. */
    public String applyStatus(Application a, String next, Long userId, String reason) {
        String prev = a.getCurrentStatus();
        Common.checkTransition(TRANSITIONS, prev, next, "application status");
        a.setCurrentStatus(next);
        if ("COMPLETED".equals(next)) a.setCompletedAt(Clock.now());
        if ("REJECTED".equals(next) || "CANCELLED".equals(next)) releaseActiveAppointment(a.getApplicationId());
        history(a.getApplicationId(), prev, next, userId, reason);
        audit.record("applications", a.getApplicationId(), "UPDATE", m("current_status", prev),
                m("current_status", next, "reason", reason), userId);
        return prev;
    }

    /** The ONE explicit exception to the transition table: an upheld appeal reopens a REJECTED application. */
    public void reopenAfterAppeal(Application a, Long userId, String note) {
        if (!"REJECTED".equals(a.getCurrentStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only REJECTED applications can be reopened (this one is " + a.getCurrentStatus() + ")");
        }
        a.setCurrentStatus("UNDER_VERIFICATION");
        String reason = "Appeal upheld" + (note != null && !note.isBlank() ? ": " + note : "");
        reason = reason.length() > 255 ? reason.substring(0, 255) : reason;
        history(a.getApplicationId(), "REJECTED", "UNDER_VERIFICATION", userId, reason);
        audit.record("applications", a.getApplicationId(), "UPDATE", m("current_status", "REJECTED"),
                m("current_status", "UNDER_VERIFICATION", "reason", reason), userId);
    }

    private static String title(String status) {
        StringBuilder sb = new StringBuilder();
        for (String w : status.split("_")) sb.append(sb.isEmpty() ? "" : " ").append(w.charAt(0)).append(w.substring(1).toLowerCase());
        return sb.toString();
    }

    public ApplicationDetail changeStatus(Long id, String next, String reasonIn, CurrentUser user) {
        Application a = get(id, true);   // row lock serialises concurrent transitions
        Common.checkTransition(TRANSITIONS, a.getCurrentStatus(), next, "application status");
        String needed = PERMISSION_FOR_TARGET.get(next);
        if (!user.has(needed)) {   // a citizen may cancel their own application without staff rights
            if (!("CANCELLED".equals(next) && user.has("application.view_own") && isOwner(user, a))) {
                throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: " + needed);
            }
        }
        String reason = reasonIn == null || reasonIn.isBlank() ? null : reasonIn.strip();
        if (REASON_REQUIRED.contains(next) && reason == null) {
            throw ApiException.badRequest("REASON_REQUIRED", "A reason is required when moving an application to " + next);
        }
        if (next.equals("APPOINTMENT_SCHEDULED") || next.equals("AWAITING_PAYMENT")) docsReady(id);
        if (next.equals("APPOINTMENT_SCHEDULED") && !db.exists("select x.appointmentId from Appointment x where x.applicationId = :a and x.status in :st",
                "a", id, "st", ACTIVE_APPOINTMENT)) {
            throw ApiException.conflict("NO_APPOINTMENT", "Book an appointment before moving to APPOINTMENT_SCHEDULED");
        }
        if (next.equals("APPROVED")) {
            ServiceType st = db.get(ServiceType.class, a.getServiceTypeId(), "Service type");
            java.math.BigDecimal fee = fees.applicationFee(st, a.getSubmittedAt().toLocalDate());
            if (fee.signum() > 0 && !hasSuccessfulPayment(id)) {
                throw ApiException.conflict("PAYMENT_REQUIRED", "Fee of " + fee.toPlainString() + " has not been paid");
            }
        }
        applyStatus(a, next, user.userId(), reason);
        db.flush();
        Citizen citizen = db.get(Citizen.class, applicantOf(a).getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Application " + title(next),
                "Application " + a.getApplicationNumber() + " is now " + next + "." + (reason != null ? " Reason: " + reason : ""));
        return detail(a);
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusHistory> history(CurrentUser user, Long id) {
        Application a = get(id, false);
        assertCanView(user, a);
        return db.list(ApplicationStatusHistory.class, "select h from ApplicationStatusHistory h where h.applicationId = :a order by h.historyId", "a", id);
    }
}
