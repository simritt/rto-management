package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.ApplicationDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.rto.core.Audit.m;

/**
 * Appointment booking. A seat can never be oversold: every booking/reschedule takes a row lock on the slot
 * (SELECT ... FOR UPDATE, always in ascending slot_id order to avoid deadlocks), re-checks capacity, then performs a
 * guarded atomic UPDATE ... WHERE booked_count < capacity. The table's CHECK (booked_count <= capacity) is the last
 * line of defence. Lock order is always application -> appointment -> slot(s).
 */
@Service
@Transactional
public class AppointmentService {
    private static final List<String> ACTIVE = ApplicationService.ACTIVE_APPOINTMENT;

    private final Db db;
    private final Audit audit;
    private final Common common;
    private final ApplicationService apps;
    private final Notifications notifications;

    public AppointmentService(Db db, Audit audit, Common common, ApplicationService apps, Notifications notifications) {
        this.db = db;
        this.audit = audit;
        this.common = common;
        this.apps = apps;
        this.notifications = notifications;
    }

    public static SlotOut slotOut(AppointmentSlot s) {
        return new SlotOut(s.getSlotId(), s.getOfficeId(), s.getCounterId(), s.getSlotDate(), s.getStartTime(), s.getEndTime(),
                s.getCapacity(), s.getBookedCount(), s.getCapacity() - s.getBookedCount());
    }

    public SlotOut createSlot(SlotCreate d, CurrentUser user) {
        common.requireActiveOffice(d.officeId());
        if (!d.endTime().isAfter(d.startTime())) {
            throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", "end_time", "message", "end_time must be after start_time")));
        }
        if (d.slotDate().isBefore(Clock.today())) throw ApiException.conflict("SLOT_IN_PAST", "Slots cannot be created in the past");
        if (d.counterId() != null) {
            Counter c = db.get(Counter.class, d.counterId(), "Counter");
            if (!c.getOfficeId().equals(d.officeId())) throw ApiException.conflict("COUNTER_OFFICE_MISMATCH", "Counter does not belong to this office");
        }
        // uq_slot includes the NULLable counter_id and MySQL treats NULLs as distinct in UNIQUE keys, so counter-less
        // duplicates must be refused here; the office row lock serialises concurrent creators.
        db.lock(RtoOffice.class, d.officeId(), "RTO office");
        String counterCond = d.counterId() == null ? "s.counterId is null" : "s.counterId = :cid";
        List<Object> kv = new ArrayList<>(List.of("o", d.officeId(), "d", d.slotDate(), "t", d.startTime()));
        if (d.counterId() != null) kv.addAll(List.of("cid", d.counterId()));
        if (db.exists("select s.slotId from AppointmentSlot s where s.officeId = :o and s.slotDate = :d and s.startTime = :t and " + counterCond, kv.toArray())) {
            throw ApiException.conflict("DUPLICATE", "An appointment slot already exists for this office/counter/date/start time");
        }
        AppointmentSlot s = new AppointmentSlot();
        s.setOfficeId(d.officeId());
        s.setCounterId(d.counterId());
        s.setSlotDate(d.slotDate());
        s.setStartTime(d.startTime());
        s.setEndTime(d.endTime());
        s.setCapacity(d.capacity());
        s.setBookedCount(0);
        db.save(s);   // uq_slot violations surface here as 409 through the global handler
        audit.record("appointment_slots", s.getSlotId(), "INSERT", null,
                m("office_id", d.officeId(), "counter_id", d.counterId(), "slot_date", d.slotDate(), "start_time", d.startTime(), "capacity", d.capacity()), user.userId());
        db.flush();
        return slotOut(s);
    }

    public SlotOut updateSlot(Long id, int capacity, CurrentUser user) {
        AppointmentSlot s = db.lock(AppointmentSlot.class, id, "Appointment slot");
        if (capacity < s.getBookedCount()) {
            throw ApiException.conflict("CAPACITY_BELOW_BOOKED", "Capacity cannot be lowered below the " + s.getBookedCount() + " seats already booked");
        }
        int old = s.getCapacity();
        s.setCapacity(capacity);
        audit.record("appointment_slots", id, "UPDATE", m("capacity", old), m("capacity", capacity), user.userId());
        db.flush();
        return slotOut(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<SlotOut> slots(PageParams p, Long officeId, Long counterId, LocalDate from, LocalDate to, boolean availableOnly) {
        QB q = new QB("s", "AppointmentSlot s").eq("s.officeId", officeId).eq("s.counterId", counterId)
                .op("s.slotDate", ">=", from).op("s.slotDate", "<=", to);
        if (availableOnly) q.and("s.bookedCount < s.capacity");
        return db.page(q, AppointmentSlot.class, p, Map.of("slot_date", "s.slotDate", "start_time", "s.startTime", "slot_id", "s.slotId"),
                "s.slotDate", false).map(AppointmentService::slotOut);
    }

    // ---- seat accounting ------------------------------------------------------------------------------

    /** Lock in ascending id order so two concurrent reschedules can never deadlock each other. */
    private Map<Long, AppointmentSlot> lockSlots(Long... ids) {
        Map<Long, AppointmentSlot> slots = new HashMap<>();
        for (Long id : new TreeSet<>(Arrays.asList(ids))) {
            AppointmentSlot s = db.find(AppointmentSlot.class, id);
            if (s == null) throw ApiException.conflict("NOT_FOUND", "Appointment slot not found");
            db.flush();
            db.em().refresh(s, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            slots.put(id, s);
        }
        return slots;
    }

    private void takeSeat(AppointmentSlot slot, Application app) {
        if (!slot.getOfficeId().equals(app.getOfficeId())) {
            throw ApiException.conflict("SLOT_OFFICE_MISMATCH", "The slot belongs to a different RTO office than the application");
        }
        LocalDateTime now = Clock.now();
        if (slot.getSlotDate().isBefore(now.toLocalDate())
                || (slot.getSlotDate().equals(now.toLocalDate()) && !slot.getEndTime().isAfter(now.toLocalTime()))) {
            throw ApiException.conflict("SLOT_IN_PAST", "This slot is in the past");
        }
        int rows = db.update("update AppointmentSlot s set s.bookedCount = s.bookedCount + 1 where s.slotId = :id and s.bookedCount < s.capacity",
                "id", slot.getSlotId());
        if (rows == 0) throw ApiException.conflict("SLOT_FULL", "This appointment slot is full");
        db.refresh(slot);
    }

    private String nextToken(AppointmentSlot slot) {
        int highest = 0;
        for (String t : db.list(String.class, "select a.tokenNumber from Appointment a where a.slotId = :s", "s", slot.getSlotId())) {
            try {
                highest = Math.max(highest, Integer.parseInt(t.substring(t.lastIndexOf('-') + 1)));
            } catch (RuntimeException ignored) {
                // foreign token format: ignore
            }
        }
        return String.format("S%d-%03d", slot.getSlotId(), highest + 1);   // slot lock is held: no two callers share a number
    }

    private Application accessApp(Appointment appt, CurrentUser user) {
        Application app = apps.get(appt.getApplicationId(), false);
        if (!(user.has("application.view") || user.has("appointment.manage") || apps.isOwner(user, app))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this appointment");
        }
        return app;
    }

    private record Loaded(Appointment appt, Application app) {}

    private Loaded loadLocked(Long appointmentId) {
        Appointment probe = db.get(Appointment.class, appointmentId, "Appointment");
        Application app = apps.get(probe.getApplicationId(), true);
        Appointment appt = db.lock(Appointment.class, appointmentId, "Appointment");
        return new Loaded(appt, app);
    }

    // ---- operations -------------------------------------------------------------------------------------

    public Appointment book(Long applicationId, Long slotId, CurrentUser user) {
        Application app = apps.get(applicationId, true);
        if (!(user.has("application.view") || apps.isOwner(user, app))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this application");
        }
        if (!Set.of("UNDER_VERIFICATION", "APPOINTMENT_SCHEDULED").contains(app.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_NOT_ELIGIBLE", "Appointments can only be booked once the application is UNDER_VERIFICATION (current status: " + app.getCurrentStatus() + ")");
        }
        Appointment existing = db.lockFirst(Appointment.class, "select a from Appointment a where a.applicationId = :a", "a", applicationId);
        if (existing != null && ACTIVE.contains(existing.getStatus())) {
            throw ApiException.conflict("ALREADY_BOOKED", "This application already has an active appointment; reschedule or cancel it");
        }
        if (existing != null && "COMPLETED".equals(existing.getStatus())) {
            throw ApiException.conflict("ALREADY_COMPLETED", "The appointment for this application has already been completed");
        }
        AppointmentSlot slot = lockSlots(slotId).get(slotId);
        takeSeat(slot, app);
        String token = nextToken(slot);
        Appointment appt;
        if (existing == null) {
            appt = new Appointment();
            appt.setApplicationId(applicationId);
            appt.setSlotId(slotId);
            appt.setTokenNumber(token);
            appt.setStatus("BOOKED");
            appt.setBookedAt(Clock.now());
            db.save(appt);
        } else {   // uq_appointment_application allows one row per application: a cancelled one is re-used
            appt = existing;
            appt.setSlotId(slotId);
            appt.setTokenNumber(token);
            appt.setStatus("BOOKED");
            appt.setBookedAt(Clock.now());
        }
        db.flush();
        if ("UNDER_VERIFICATION".equals(app.getCurrentStatus())) apps.applyStatus(app, "APPOINTMENT_SCHEDULED", user.userId(), "Appointment booked");
        audit.record("appointments", appt.getAppointmentId(), "INSERT", null,
                m("application_id", applicationId, "slot_id", slotId, "token_number", token), user.userId());
        db.flush();
        Citizen citizen = db.get(Citizen.class, apps.applicantOf(app).getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Appointment booked", "Appointment booked for " + slot.getSlotDate() + " "
                + String.format("%02d:%02d", slot.getStartTime().getHour(), slot.getStartTime().getMinute()) + ". Token " + token + ".");
        return appt;
    }

    public Appointment cancel(Long appointmentId, CurrentUser user) {
        Loaded l = loadLocked(appointmentId);
        accessApp(l.appt(), user);
        if (!ACTIVE.contains(l.appt().getStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "A " + l.appt().getStatus() + " appointment cannot be cancelled");
        }
        lockSlots(l.appt().getSlotId());
        apps.releaseSeat(l.appt().getSlotId());
        l.appt().setStatus("CANCELLED");
        if ("APPOINTMENT_SCHEDULED".equals(l.app().getCurrentStatus())) {
            apps.applyStatus(l.app(), "UNDER_VERIFICATION", user.userId(), "Appointment cancelled");
        }
        audit.record("appointments", appointmentId, "UPDATE", m("status", "BOOKED"), m("status", "CANCELLED"), user.userId());
        db.flush();
        Citizen citizen = db.get(Citizen.class, apps.applicantOf(l.app()).getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Appointment cancelled", "Your appointment (token " + l.appt().getTokenNumber() + ") was cancelled.");
        return l.appt();
    }

    public Appointment reschedule(Long appointmentId, Long newSlotId, CurrentUser user) {
        Loaded l = loadLocked(appointmentId);
        accessApp(l.appt(), user);
        Appointment appt = l.appt();
        if (!ACTIVE.contains(appt.getStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "A " + appt.getStatus() + " appointment cannot be rescheduled");
        }
        if (appt.getSlotId().equals(newSlotId)) throw ApiException.conflict("SAME_SLOT", "The appointment is already in this slot");
        Long oldSlotId = appt.getSlotId();
        Map<Long, AppointmentSlot> slots = lockSlots(oldSlotId, newSlotId);   // both locked, lowest id first
        takeSeat(slots.get(newSlotId), l.app());   // fails with 409 and changes nothing if the new slot is full
        apps.releaseSeat(oldSlotId);
        appt.setSlotId(newSlotId);
        appt.setStatus("RESCHEDULED");
        appt.setTokenNumber(nextToken(slots.get(newSlotId)));
        audit.record("appointments", appointmentId, "UPDATE", m("slot_id", oldSlotId), m("slot_id", newSlotId, "status", "RESCHEDULED"), user.userId());
        db.flush();
        Citizen citizen = db.get(Citizen.class, apps.applicantOf(l.app()).getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Appointment rescheduled",
                "Your appointment moved to " + slots.get(newSlotId).getSlotDate() + ". Token " + appt.getTokenNumber() + ".");
        return appt;
    }

    public Appointment outcome(Long appointmentId, String status, CurrentUser user) {
        Appointment appt = db.lock(Appointment.class, appointmentId, "Appointment");
        if (!ACTIVE.contains(appt.getStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "A " + appt.getStatus() + " appointment cannot be marked " + status);
        }
        String old = appt.getStatus();
        appt.setStatus(status);   // the seat stays consumed: the slot time has passed
        audit.record("appointments", appointmentId, "UPDATE", m("status", old), m("status", status), user.userId());
        db.flush();
        return appt;
    }

    @Transactional(readOnly = true)
    public PageResponse<Appointment> list(CurrentUser user, PageParams p, Long officeId, LocalDate slotDate, String status, Long applicationId) {
        QB q = new QB("a", "Appointment a join AppointmentSlot s on s.slotId = a.slotId "
                + "join Application ap on ap.applicationId = a.applicationId join Applicant apc on apc.applicantId = ap.applicantId");
        if (!user.has("appointment.view")) {
            if (!user.has("application.view_own")) throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: appointment.view");
            q.and("apc.citizenId = :me", "me", user.citizenId() == null ? 0L : user.citizenId());
        }
        q.eq("s.officeId", officeId).eq("s.slotDate", slotDate).eq("a.status", status).eq("a.applicationId", applicationId);
        return db.page(q, Appointment.class, p, Map.of("booked_at", "a.bookedAt", "slot_date", "s.slotDate", "appointment_id", "a.appointmentId"),
                "a.appointmentId", true);
    }
}
