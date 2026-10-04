package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.LicenceDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.rto.core.Audit.m;

/** Learner licences, driving licences (+classes, status history) and driving tests. */
@Service
@Transactional
public class LicenceService {
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "ACTIVE", Set.of("SUSPENDED", "REVOKED", "EXPIRED"),
            "SUSPENDED", Set.of("ACTIVE", "REVOKED"),
            "EXPIRED", Set.of("ACTIVE"),   // via renewal
            "REVOKED", Set.of());

    private final Db db;
    private final Audit audit;
    private final Common common;
    private final ApplicationService apps;
    private final Notifications notifications;

    public LicenceService(Db db, Audit audit, Common common, ApplicationService apps, Notifications notifications) {
        this.db = db;
        this.audit = audit;
        this.common = common;
        this.apps = apps;
        this.notifications = notifications;
    }

    // ---- schools / instructors / test centres ---------------------------------------------------------------

    public DrivingSchool createSchool(SchoolCreate d, CurrentUser user) {
        common.requireActiveOffice(d.officeId());
        if (db.exists("select s.schoolId from DrivingSchool s where s.licenceNumber = :n", "n", d.licenceNumber())) {
            throw ApiException.conflict("DUPLICATE", "Driving school licence number already exists");
        }
        DrivingSchool s = new DrivingSchool();
        s.setSchoolName(d.schoolName());
        s.setLicenceNumber(d.licenceNumber());
        s.setOfficeId(d.officeId());
        db.save(s);
        audit.record("driving_schools", s.getSchoolId(), "INSERT", null, m("school_name", d.schoolName(), "licence_number", d.licenceNumber(), "office_id", d.officeId()), user.userId());
        db.flush();
        return s;
    }

    public DrivingSchool updateSchool(Long id, SchoolUpdate d, CurrentUser user) {
        DrivingSchool s = db.lock(DrivingSchool.class, id, "Driving school");
        Map<String, Object> old = m(), neu = m();
        if (d.officeId() != null) {
            common.requireActiveOffice(d.officeId());
            old.put("office_id", s.getOfficeId());
            neu.put("office_id", d.officeId());
            s.setOfficeId(d.officeId());
        }
        if (d.schoolName() != null) {
            old.put("school_name", s.getSchoolName());
            neu.put("school_name", d.schoolName());
            s.setSchoolName(d.schoolName());
        }
        if (!neu.isEmpty()) audit.record("driving_schools", id, "UPDATE", old, neu, user.userId());
        db.flush();
        return s;
    }

    @Transactional(readOnly = true)
    public PageResponse<DrivingSchool> schools(PageParams p, Long officeId) {
        QB q = new QB("s", "DrivingSchool s").search(p.search(), "s.schoolName", "s.licenceNumber").eq("s.officeId", officeId);
        return db.page(q, DrivingSchool.class, p, Map.of("school_name", "s.schoolName", "school_id", "s.schoolId"), "s.schoolId", false);
    }

    @Transactional(readOnly = true)
    public DrivingSchool school(Long id) {
        return db.get(DrivingSchool.class, id, "Driving school");
    }

    private InstructorOut instructorOut(DrivingInstructor i) {
        Person p = db.get(Person.class, i.getPersonId(), "Person");
        return new InstructorOut(i.getInstructorId(), i.getPersonId(), i.getSchoolId(), i.getLicenceClassId(), p.getFirstName(), p.getLastName());
    }

    public InstructorOut addInstructor(Long schoolId, InstructorCreate d, CurrentUser user) {
        db.get(DrivingSchool.class, schoolId, "Driving school");
        db.get(LicenceClass.class, d.licenceClassId(), "Licence class");
        Person person = common.resolvePerson(d.personId(), d.person());
        if (db.exists("select i.instructorId from DrivingInstructor i where i.personId = :p", "p", person.getPersonId())) {
            throw ApiException.conflict("DUPLICATE", "This person is already a driving instructor");
        }
        DrivingInstructor i = new DrivingInstructor();
        i.setPersonId(person.getPersonId());
        i.setSchoolId(schoolId);
        i.setLicenceClassId(d.licenceClassId());
        db.save(i);
        audit.record("driving_instructors", i.getInstructorId(), "INSERT", null,
                m("school_id", schoolId, "person_id", person.getPersonId(), "licence_class_id", d.licenceClassId()), user.userId());
        db.flush();
        return instructorOut(i);
    }

    @Transactional(readOnly = true)
    public List<InstructorOut> instructors(Long schoolId) {
        db.get(DrivingSchool.class, schoolId, "Driving school");
        return db.list(DrivingInstructor.class, "select i from DrivingInstructor i where i.schoolId = :s order by i.instructorId", "s", schoolId)
                .stream().map(this::instructorOut).toList();
    }

    public TestCentre createTestCentre(TestCentreCreate d, CurrentUser user) {
        common.requireActiveOffice(d.officeId());
        TestCentre t = new TestCentre();
        t.setOfficeId(d.officeId());
        t.setCentreName(d.centreName());
        db.save(t);
        audit.record("test_centres", t.getTestCentreId(), "INSERT", null, m("office_id", d.officeId(), "centre_name", d.centreName()), user.userId());
        db.flush();
        return t;
    }

    @Transactional(readOnly = true)
    public List<TestCentre> testCentres(Long officeId) {
        return officeId == null ? db.list(TestCentre.class, "select t from TestCentre t order by t.testCentreId")
                : db.list(TestCentre.class, "select t from TestCentre t where t.officeId = :o order by t.testCentreId", "o", officeId);
    }

    // ---- shared checks ----------------------------------------------------------------------------------------

    private Application eligibleApplication(Long applicationId, Long citizenId) {
        Application app = apps.get(applicationId, true);
        if (!apps.applicantOf(app).getCitizenId().equals(citizenId)) {
            throw ApiException.conflict("APPLICATION_CITIZEN_MISMATCH", "The application belongs to a different citizen");
        }
        if (!Set.of("APPROVED", "COMPLETED").contains(app.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_NOT_APPROVED", "The application must be APPROVED before a licence is issued (it is " + app.getCurrentStatus() + ")");
        }
        return app;
    }

    private void completeApplication(Application app, Long userId, String reason) {
        if ("APPROVED".equals(app.getCurrentStatus())) apps.applyStatus(app, "COMPLETED", userId, reason);
    }

    private Citizen activeCitizen(Long citizenId) {
        Citizen c = db.lock(Citizen.class, citizenId, "Citizen");
        if (Boolean.TRUE.equals(c.getBlacklisted())) throw ApiException.conflict("CITIZEN_BLACKLISTED", "Blacklisted citizens cannot be issued licences");
        return c;
    }

    private static void checkDates(LocalDate issue, LocalDate expiry) {
        if (!expiry.isAfter(issue)) {
            throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", "expiry_date", "message", "expiry_date must be after issue_date")));
        }
    }

    // ---- learner licences ------------------------------------------------------------------------------------

    public LearnerLicence createLearner(LearnerCreate d, CurrentUser user) {
        if (d.issueDate() != null && d.expiryDate() != null) checkDates(d.issueDate(), d.expiryDate());
        Citizen citizen = activeCitizen(d.citizenId());
        common.requireActiveOffice(d.officeId());
        Application app = eligibleApplication(d.applicationId(), d.citizenId());
        LocalDate issue = d.issueDate() != null ? d.issueDate() : Clock.today();
        LocalDate expiry = d.expiryDate() != null ? d.expiryDate() : issue.plusMonths(6);
        if (!expiry.isAfter(issue)) throw ApiException.badRequest("INVALID_DATES", "expiry_date must be after issue_date");

        LearnerLicence existing = db.lockFirst(LearnerLicence.class, "select l from LearnerLicence l where l.citizenId = :c and l.status = 'ACTIVE'", "c", d.citizenId());
        if (existing != null) {
            if (existing.getExpiryDate().isBefore(Clock.today())) {   // lapsed but never swept: close it so the new one may be issued
                existing.setStatus("EXPIRED");
                audit.record("learner_licences", existing.getLearnerLicenceId(), "UPDATE", m("status", "ACTIVE"), m("status", "EXPIRED"), user.userId());
                db.flush();
            } else {
                throw ApiException.conflict("ACTIVE_LEARNER_EXISTS", "Citizen already has an ACTIVE learner licence");
            }
        }
        String number = d.licenceNumber() != null ? d.licenceNumber() : Common.genNumber("LL");
        if (db.exists("select l.learnerLicenceId from LearnerLicence l where l.licenceNumber = :n", "n", number)) {
            throw ApiException.conflict("DUPLICATE", "Learner licence number already exists");
        }
        LearnerLicence ll = new LearnerLicence();
        ll.setLicenceNumber(number);
        ll.setCitizenId(d.citizenId());
        ll.setApplicationId(d.applicationId());
        ll.setOfficeId(d.officeId());
        ll.setIssueDate(issue);
        ll.setExpiryDate(expiry);
        ll.setStatus("ACTIVE");
        db.save(ll);   // uq_citizen_active_learner is the database-level guarantee behind the check above
        audit.record("learner_licences", ll.getLearnerLicenceId(), "INSERT", null,
                m("licence_number", number, "citizen_id", d.citizenId(), "issue_date", issue, "expiry_date", expiry), user.userId());
        completeApplication(app, user.userId(), "Learner licence " + number + " issued");
        db.flush();
        notifications.notify(citizen.getPersonId(), "Learner licence issued", "Learner licence " + number + " is valid until " + expiry + ".");
        return ll;
    }

    public LearnerLicence cancelLearner(Long id, String reason, CurrentUser user) {
        LearnerLicence ll = db.lock(LearnerLicence.class, id, "Learner licence");
        if (!"ACTIVE".equals(ll.getStatus())) throw ApiException.conflict("INVALID_TRANSITION", "A " + ll.getStatus() + " learner licence cannot be cancelled");
        ll.setStatus("CANCELLED");
        audit.record("learner_licences", id, "UPDATE", m("status", "ACTIVE"), m("status", "CANCELLED", "reason", reason), user.userId());
        db.flush();
        return ll;
    }

    @Transactional(readOnly = true)
    public LearnerLicence learner(CurrentUser user, Long id) {
        LearnerLicence ll = db.get(LearnerLicence.class, id, "Learner licence");
        if (!(user.has("licence.view") || user.isCitizen(ll.getCitizenId()))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: licence.view");
        }
        return ll;
    }

    @Transactional(readOnly = true)
    public PageResponse<LearnerLicence> learners(PageParams p, Long citizenId, String status, Long officeId) {
        QB q = new QB("l", "LearnerLicence l").search(p.search(), "l.licenceNumber").eq("l.citizenId", citizenId)
                .eq("l.status", status).eq("l.officeId", officeId);
        return db.page(q, LearnerLicence.class, p, Map.of("issue_date", "l.issueDate", "expiry_date", "l.expiryDate",
                "licence_number", "l.licenceNumber", "learner_licence_id", "l.learnerLicenceId"), "l.learnerLicenceId", true);
    }

    // ---- driving licences ------------------------------------------------------------------------------------

    public DrivingLicenceOut licenceOut(DrivingLicence dl) {
        List<ClassOut> classes = new ArrayList<>();
        for (LicenceClassAssignment a : db.list(LicenceClassAssignment.class, "select a from LicenceClassAssignment a where a.drivingLicenceId = :l order by a.licenceClassId", "l", dl.getDrivingLicenceId())) {
            classes.add(new ClassOut(a.getLicenceClassId(), db.get(LicenceClass.class, a.getLicenceClassId(), "Licence class").getClassCode(), a.getGrantedOn()));
        }
        return new DrivingLicenceOut(dl.getDrivingLicenceId(), dl.getLicenceNumber(), dl.getCitizenId(), dl.getLearnerLicenceId(),
                dl.getApplicationId(), dl.getOfficeId(), dl.getIssueDate(), dl.getExpiryDate(), dl.getCurrentStatus(), classes);
    }

    public DrivingLicence getLicence(Long id, boolean lock) {
        return lock ? db.lock(DrivingLicence.class, id, "Driving licence") : db.get(DrivingLicence.class, id, "Driving licence");
    }

    public void assertCanView(CurrentUser user, DrivingLicence dl) {
        if (user.has("licence.view") || user.isCitizen(dl.getCitizenId())) return;
        throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: licence.view");
    }

    @Transactional(readOnly = true)
    public DrivingLicenceOut view(CurrentUser user, Long id) {
        DrivingLicence dl = getLicence(id, false);
        assertCanView(user, dl);
        return licenceOut(dl);
    }

    private void history(Long licenceId, String prev, String next, Long userId, String reason) {
        LicenceStatusHistory h = new LicenceStatusHistory();
        h.setDrivingLicenceId(licenceId);
        h.setPreviousStatus(prev);
        h.setNewStatus(next);
        h.setChangedByUserId(userId);
        h.setReason(reason);
        db.save(h);
    }

    public DrivingLicenceOut createDrivingLicence(DrivingLicenceCreate d, CurrentUser user) {
        if (d.issueDate() != null && d.expiryDate() != null) checkDates(d.issueDate(), d.expiryDate());
        Citizen citizen = activeCitizen(d.citizenId());
        common.requireActiveOffice(d.officeId());
        Application app = eligibleApplication(d.applicationId(), d.citizenId());

        String result = db.first(String.class, "select t.result from DrivingTest t where t.applicationId = :a and t.citizenId = :c "
                + "order by t.scheduledAt desc, t.testId desc", "a", d.applicationId(), "c", d.citizenId());
        if (result != null && !result.equals("PASS")) {
            throw ApiException.conflict("TEST_NOT_PASSED", "The latest driving test for this application is " + result + "; a PASS is required");
        }
        List<Long> classIds = d.licenceClassIds().stream().distinct().sorted().toList();
        List<LicenceClass> found = db.list(LicenceClass.class, "select c from LicenceClass c where c.licenceClassId in :ids", "ids", classIds);
        if (found.size() != classIds.size()) throw ApiException.badRequest("INVALID_REFERENCE", "One or more licence classes do not exist");

        LearnerLicence learner = null;
        if (d.learnerLicenceId() != null) {
            learner = db.lock(LearnerLicence.class, d.learnerLicenceId(), "Learner licence");
            if (!learner.getCitizenId().equals(d.citizenId())) throw ApiException.conflict("LEARNER_CITIZEN_MISMATCH", "The learner licence belongs to a different citizen");
            if (!"ACTIVE".equals(learner.getStatus())) throw ApiException.conflict("LEARNER_NOT_ACTIVE", "The learner licence is " + learner.getStatus() + ", not ACTIVE");
        }
        LocalDate issue = d.issueDate() != null ? d.issueDate() : Clock.today();
        LocalDate expiry = d.expiryDate() != null ? d.expiryDate() : issue.plusMonths(240);
        if (!expiry.isAfter(issue)) throw ApiException.badRequest("INVALID_DATES", "expiry_date must be after issue_date");
        String number = d.licenceNumber() != null ? d.licenceNumber() : Common.genNumber("DL");
        if (db.exists("select l.drivingLicenceId from DrivingLicence l where l.licenceNumber = :n", "n", number)) {
            throw ApiException.conflict("DUPLICATE", "Driving licence number already exists");
        }
        DrivingLicence dl = new DrivingLicence();
        dl.setLicenceNumber(number);
        dl.setCitizenId(d.citizenId());
        dl.setLearnerLicenceId(d.learnerLicenceId());
        dl.setApplicationId(d.applicationId());
        dl.setOfficeId(d.officeId());
        dl.setIssueDate(issue);
        dl.setExpiryDate(expiry);
        dl.setCurrentStatus("ACTIVE");
        db.save(dl);
        for (LicenceClass c : found) {
            LicenceClassAssignment a = new LicenceClassAssignment();
            a.setDrivingLicenceId(dl.getDrivingLicenceId());
            a.setLicenceClassId(c.getLicenceClassId());
            a.setGrantedOn(issue);
            db.save(a);
        }
        history(dl.getDrivingLicenceId(), null, "ACTIVE", user.userId(), "Licence issued");
        if (learner != null) {
            learner.setStatus("CONVERTED");
            audit.record("learner_licences", learner.getLearnerLicenceId(), "UPDATE", m("status", "ACTIVE"), m("status", "CONVERTED"), user.userId());
        }
        audit.record("driving_licences", dl.getDrivingLicenceId(), "INSERT", null,
                m("licence_number", number, "citizen_id", d.citizenId(), "classes", found.stream().map(LicenceClass::getClassCode).toList(),
                        "issue_date", issue, "expiry_date", expiry), user.userId());
        completeApplication(app, user.userId(), "Driving licence " + number + " issued");
        db.flush();
        notifications.notify(citizen.getPersonId(), "Driving licence issued", "Driving licence " + number + " is valid until " + expiry + ".");
        return licenceOut(dl);
    }

    public DrivingLicenceOut addClass(Long licenceId, Long classId, CurrentUser user) {
        DrivingLicence dl = getLicence(licenceId, true);
        if (!"ACTIVE".equals(dl.getCurrentStatus())) {
            throw ApiException.conflict("INVALID_STATE", "Classes can only be added to an ACTIVE licence (it is " + dl.getCurrentStatus() + ")");
        }
        db.get(LicenceClass.class, classId, "Licence class");
        if (db.exists("select a.licenceClassId from LicenceClassAssignment a where a.drivingLicenceId = :l and a.licenceClassId = :c", "l", licenceId, "c", classId)) {
            throw ApiException.conflict("DUPLICATE", "The licence already holds this class");
        }
        LicenceClassAssignment a = new LicenceClassAssignment();
        a.setDrivingLicenceId(licenceId);
        a.setLicenceClassId(classId);
        a.setGrantedOn(Clock.today());
        db.save(a);
        audit.record("licence_class_assignments", licenceId, "INSERT", null, m("licence_class_id", classId), user.userId());
        db.flush();
        return licenceOut(dl);
    }

    /** Validated status change + history row + audit; the CALLER owns the transaction. Returns the previous status. */
    public String applyLicenceStatus(DrivingLicence dl, String next, String reason, Long userId, LocalDate newExpiry) {
        String prev = dl.getCurrentStatus();
        Common.checkTransition(TRANSITIONS, prev, next, "licence status");
        Map<String, Object> neu = m("current_status", next, "reason", reason);
        if (newExpiry != null) {
            if ("EXPIRED".equals(prev) && !newExpiry.isAfter(dl.getExpiryDate().isAfter(Clock.today()) ? dl.getExpiryDate() : Clock.today())) {
                throw ApiException.badRequest("INVALID_DATES", "The new expiry date must be in the future");
            }
            if (!newExpiry.isAfter(dl.getIssueDate())) throw ApiException.badRequest("INVALID_DATES", "new_expiry_date must be after issue_date");
            neu.put("expiry_date", newExpiry);
            dl.setExpiryDate(newExpiry);
        }
        dl.setCurrentStatus(next);
        history(dl.getDrivingLicenceId(), prev, next, userId, reason);
        audit.record("driving_licences", dl.getDrivingLicenceId(), "UPDATE", m("current_status", prev), neu, userId);
        return prev;
    }

    public DrivingLicenceOut changeStatus(Long licenceId, String next, String reason, CurrentUser user, LocalDate newExpiry) {
        DrivingLicence dl = getLicence(licenceId, true);   // row lock: concurrent suspend/revoke serialise
        applyLicenceStatus(dl, next, reason, user.userId(), newExpiry);
        db.flush();
        Citizen citizen = db.get(Citizen.class, dl.getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Licence " + next.charAt(0) + next.substring(1).toLowerCase(),
                "Driving licence " + dl.getLicenceNumber() + " is now " + next + ". Reason: " + reason);
        return licenceOut(dl);
    }

    public DrivingLicenceOut renew(Long licenceId, LocalDate newExpiry, String reason, CurrentUser user) {
        DrivingLicence dl = getLicence(licenceId, true);
        if ("ACTIVE".equals(dl.getCurrentStatus())) {   // early renewal: extend only
            if (!newExpiry.isAfter(dl.getExpiryDate())) throw ApiException.badRequest("INVALID_DATES", "new_expiry_date must be after the current expiry date");
            LocalDate old = dl.getExpiryDate();
            dl.setExpiryDate(newExpiry);
            history(licenceId, "ACTIVE", "ACTIVE", user.userId(), reason);
            audit.record("driving_licences", licenceId, "UPDATE", m("expiry_date", old), m("expiry_date", newExpiry, "reason", reason), user.userId());
            db.flush();
            return licenceOut(dl);
        }
        return changeStatus(licenceId, "ACTIVE", reason, user, newExpiry);
    }

    public Map<String, Object> expireOverdue(CurrentUser user) {
        int dls = 0, lls = 0;
        for (DrivingLicence dl : db.lockList(DrivingLicence.class, "select l from DrivingLicence l where l.currentStatus = 'ACTIVE' and l.expiryDate < :t", "t", Clock.today())) {
            dl.setCurrentStatus("EXPIRED");
            history(dl.getDrivingLicenceId(), "ACTIVE", "EXPIRED", user.userId(), "Expired on schedule");
            audit.record("driving_licences", dl.getDrivingLicenceId(), "UPDATE", m("current_status", "ACTIVE"), m("current_status", "EXPIRED"), user.userId());
            dls++;
        }
        for (LearnerLicence ll : db.lockList(LearnerLicence.class, "select l from LearnerLicence l where l.status = 'ACTIVE' and l.expiryDate < :t", "t", Clock.today())) {
            ll.setStatus("EXPIRED");
            audit.record("learner_licences", ll.getLearnerLicenceId(), "UPDATE", m("status", "ACTIVE"), m("status", "EXPIRED"), user.userId());
            lls++;
        }
        db.flush();
        return m("driving_licences_expired", dls, "learner_licences_expired", lls);
    }

    /** Notify holders of ACTIVE licences that expire within `days` (notification failures never raise). */
    public ReminderResult sendExpiryReminders(int days) {
        List<Object[]> rows = db.rows("select l.licenceNumber, l.expiryDate, c.personId from DrivingLicence l join Citizen c on c.citizenId = l.citizenId "
                + "where l.currentStatus = 'ACTIVE' and l.expiryDate between :a and :b", "a", Clock.today(), "b", Clock.today().plusDays(days));
        for (Object[] r : rows) {
            notifications.notify((Long) r[2], "Licence nearing expiry", "Driving licence " + r[0] + " expires on " + r[1] + ". Please renew it.");
        }
        return new ReminderResult(rows.size());
    }

    @Transactional(readOnly = true)
    public List<LicenceStatusHistory> history(CurrentUser user, Long licenceId) {
        DrivingLicence dl = getLicence(licenceId, false);
        assertCanView(user, dl);
        return db.list(LicenceStatusHistory.class, "select h from LicenceStatusHistory h where h.drivingLicenceId = :l order by h.historyId", "l", licenceId);
    }

    @Transactional(readOnly = true)
    public PageResponse<DrivingLicenceOut> licences(PageParams p, String status, Long citizenId, Long officeId, Long classId, Integer expiringWithinDays) {
        QB q = new QB("l", "DrivingLicence l join Citizen c on c.citizenId = l.citizenId join Person pr on pr.personId = c.personId")
                .select("l").search(p.search(), "l.licenceNumber", "pr.firstName", "pr.lastName", "c.citizenCode")
                .eq("l.currentStatus", status).eq("l.citizenId", citizenId).eq("l.officeId", officeId);
        if (classId != null) q.and("l.drivingLicenceId in (select a.drivingLicenceId from LicenceClassAssignment a where a.licenceClassId = :cls)", "cls", classId);
        if (expiringWithinDays != null) {
            q.and("l.currentStatus = 'ACTIVE' and l.expiryDate between :ea and :eb", "ea", Clock.today(), "eb", Clock.today().plusDays(expiringWithinDays));
        }
        return db.page(q, DrivingLicence.class, p, Map.of("issue_date", "l.issueDate", "expiry_date", "l.expiryDate",
                "licence_number", "l.licenceNumber", "driving_licence_id", "l.drivingLicenceId"), "l.drivingLicenceId", true).map(this::licenceOut);
    }

    // ---- driving tests -----------------------------------------------------------------------------------------

    public DrivingTest scheduleTest(TestCreate d, CurrentUser user) {
        Application app = apps.get(d.applicationId(), true);
        if (!apps.applicantOf(app).getCitizenId().equals(d.citizenId())) {
            throw ApiException.conflict("APPLICATION_CITIZEN_MISMATCH", "The application belongs to a different citizen");
        }
        if (ApplicationService.TERMINAL.contains(app.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_CLOSED", "Tests cannot be scheduled for a " + app.getCurrentStatus() + " application");
        }
        TestCentre centre = db.get(TestCentre.class, d.testCentreId(), "Test centre");
        common.requireActiveOffice(centre.getOfficeId());
        common.requireActiveEmployee(d.examinerEmployeeId(), "Examiner");
        if (!d.scheduledAt().isAfter(Clock.now())) throw ApiException.conflict("SCHEDULE_IN_PAST", "A test must be scheduled in the future");
        List<DrivingTest> prior = db.list(DrivingTest.class, "select t from DrivingTest t where t.applicationId = :a and t.citizenId = :c order by t.testId", "a", d.applicationId(), "c", d.citizenId());
        if (prior.stream().anyMatch(t -> t.getResult().equals("PENDING"))) throw ApiException.conflict("TEST_PENDING", "A test is already pending for this application");
        if (prior.stream().anyMatch(t -> t.getResult().equals("PASS"))) throw ApiException.conflict("TEST_ALREADY_PASSED", "The applicant has already passed the test for this application");
        DrivingTest t = new DrivingTest();   // retakes simply add another row
        t.setApplicationId(d.applicationId());
        t.setCitizenId(d.citizenId());
        t.setTestCentreId(d.testCentreId());
        t.setExaminerEmployeeId(d.examinerEmployeeId());
        t.setScheduledAt(d.scheduledAt());
        t.setResult("PENDING");
        t.setRemarks(d.remarks());
        db.save(t);
        audit.record("driving_tests", t.getTestId(), "INSERT", null, m("application_id", d.applicationId(), "scheduled_at", d.scheduledAt(), "attempt", prior.size() + 1), user.userId());
        db.flush();
        return t;
    }

    public DrivingTest recordResult(Long testId, String result, String remarks, CurrentUser user) {
        DrivingTest t = db.lock(DrivingTest.class, testId, "Driving test");
        if (!"PENDING".equals(t.getResult())) throw ApiException.conflict("RESULT_ALREADY_RECORDED", "The result of this test is already recorded (" + t.getResult() + ")");
        t.setResult(result);
        if (remarks != null) t.setRemarks(remarks);
        audit.record("driving_tests", testId, "UPDATE", m("result", "PENDING"), m("result", result), user.userId());
        db.flush();
        Citizen citizen = db.get(Citizen.class, t.getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Driving test result", "Your driving test result: " + result + ".");
        return t;
    }

    @Transactional(readOnly = true)
    public DrivingTest test(Long id) {
        return db.get(DrivingTest.class, id, "Driving test");
    }

    @Transactional(readOnly = true)
    public PageResponse<DrivingTest> tests(PageParams p, Long applicationId, Long citizenId, String result, Long centreId) {
        QB q = new QB("t", "DrivingTest t").eq("t.applicationId", applicationId).eq("t.citizenId", citizenId)
                .eq("t.result", result).eq("t.testCentreId", centreId);
        return db.page(q, DrivingTest.class, p, Map.of("scheduled_at", "t.scheduledAt", "test_id", "t.testId"), "t.testId", true);
    }
}
