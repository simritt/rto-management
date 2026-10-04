package com.rto.web;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.LicenceDto.*;
import com.rto.service.LicenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "licences")
public class LicenceController {
    private final LicenceService svc;

    public LicenceController(LicenceService svc) {
        this.svc = svc;
    }

    // ---- schools / instructors / test centres -------------------------------------------------------------

    @PostMapping("/driving-schools")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("licence.manage_schools")
    public DrivingSchool createSchool(@Valid @RequestBody SchoolCreate body, CurrentUser user) {
        return svc.createSchool(body, user);
    }

    @GetMapping("/driving-schools")
    @Requires("licence.view")
    public PageResponse<DrivingSchool> schools(@RequestParam(name = "office_id", required = false) Long officeId, PageParams p) {
        return svc.schools(p, officeId);
    }

    @GetMapping("/driving-schools/{schoolId}")
    @Requires("licence.view")
    public DrivingSchool school(@PathVariable Long schoolId) {
        return svc.school(schoolId);
    }

    @PatchMapping("/driving-schools/{schoolId}")
    @Requires("licence.manage_schools")
    public DrivingSchool updateSchool(@PathVariable Long schoolId, @Valid @RequestBody SchoolUpdate body, CurrentUser user) {
        return svc.updateSchool(schoolId, body, user);
    }

    @PostMapping("/driving-schools/{schoolId}/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("licence.manage_schools")
    public InstructorOut addInstructor(@PathVariable Long schoolId, @Valid @RequestBody InstructorCreate body, CurrentUser user) {
        return svc.addInstructor(schoolId, body, user);
    }

    @GetMapping("/driving-schools/{schoolId}/instructors")
    @Requires("licence.view")
    public List<InstructorOut> instructors(@PathVariable Long schoolId) {
        return svc.instructors(schoolId);
    }

    @PostMapping("/test-centres")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("licence.manage_schools")
    public TestCentre createTestCentre(@Valid @RequestBody TestCentreCreate body, CurrentUser user) {
        return svc.createTestCentre(body, user);
    }

    @GetMapping("/test-centres")
    @Requires("licence.view")
    public List<TestCentre> testCentres(@RequestParam(name = "office_id", required = false) Long officeId) {
        return svc.testCentres(officeId);
    }

    // ---- learner licences -----------------------------------------------------------------------------------

    @PostMapping("/learner-licences")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("licence.issue")
    @Operation(summary = "Issue a learner licence (one ACTIVE per citizen; application must be APPROVED)")
    public LearnerLicence createLearner(@Valid @RequestBody LearnerCreate body, CurrentUser user) {
        return svc.createLearner(body, user);
    }

    @GetMapping("/learner-licences")
    @Requires("licence.view")
    public PageResponse<LearnerLicence> learners(@RequestParam(name = "citizen_id", required = false) Long citizenId,
                                                 @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                                 @RequestParam(name = "office_id", required = false) Long officeId, PageParams p) {
        return svc.learners(p, citizenId, status, officeId);
    }

    @GetMapping("/learner-licences/{learnerId}")
    public LearnerLicence learner(@PathVariable Long learnerId, CurrentUser user) {
        return svc.learner(user, learnerId);
    }

    @PostMapping("/learner-licences/{learnerId}/cancel")
    @Requires("licence.revoke")
    public LearnerLicence cancelLearner(@PathVariable Long learnerId, @Valid @RequestBody StatusReason body, CurrentUser user) {
        return svc.cancelLearner(learnerId, body.reason(), user);
    }

    // ---- driving licences -----------------------------------------------------------------------------------

    @PostMapping("/driving-licences")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("licence.issue")
    @Operation(summary = "Issue a driving licence with one or more classes (application must be APPROVED)")
    public DrivingLicenceOut createLicence(@Valid @RequestBody DrivingLicenceCreate body, CurrentUser user) {
        return svc.createDrivingLicence(body, user);
    }

    @GetMapping("/driving-licences")
    @Requires("licence.view")
    public PageResponse<DrivingLicenceOut> licences(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                                    @RequestParam(name = "citizen_id", required = false) Long citizenId,
                                                    @RequestParam(name = "office_id", required = false) Long officeId,
                                                    @RequestParam(name = "class_id", required = false) Long classId,
                                                    @RequestParam(name = "expiring_within_days", required = false) @Min(0) @Max(3650) Integer expiring,
                                                    PageParams p) {
        return svc.licences(p, status, citizenId, officeId, classId, expiring);
    }

    @PostMapping("/driving-licences/expire-overdue")
    @Requires("licence.revoke")
    @Operation(summary = "Mark lapsed ACTIVE licences EXPIRED (with history)")
    public Map<String, Object> expireOverdue(CurrentUser user) {
        return svc.expireOverdue(user);
    }

    @GetMapping("/driving-licences/{licenceId}")
    public DrivingLicenceOut licence(@PathVariable Long licenceId, CurrentUser user) {
        return svc.view(user, licenceId);
    }

    @PostMapping("/driving-licences/{licenceId}/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("licence.issue")
    public DrivingLicenceOut addClass(@PathVariable Long licenceId, @Valid @RequestBody AddClass body, CurrentUser user) {
        return svc.addClass(licenceId, body.licenceClassId(), user);
    }

    @PostMapping("/driving-licences/{licenceId}/suspend")
    @Requires("licence.suspend")
    @Operation(summary = "ACTIVE -> SUSPENDED with reason, history row and audit")
    public DrivingLicenceOut suspend(@PathVariable Long licenceId, @Valid @RequestBody StatusReason body, CurrentUser user) {
        return svc.changeStatus(licenceId, "SUSPENDED", body.reason(), user, null);
    }

    @PostMapping("/driving-licences/{licenceId}/revoke")
    @Requires("licence.revoke")
    @Operation(summary = "ACTIVE/SUSPENDED -> REVOKED (terminal)")
    public DrivingLicenceOut revoke(@PathVariable Long licenceId, @Valid @RequestBody StatusReason body, CurrentUser user) {
        return svc.changeStatus(licenceId, "REVOKED", body.reason(), user, null);
    }

    @PostMapping("/driving-licences/{licenceId}/reinstate")
    @Requires("licence.issue")
    @Operation(summary = "SUSPENDED -> ACTIVE")
    public DrivingLicenceOut reinstate(@PathVariable Long licenceId, @Valid @RequestBody StatusReason body, CurrentUser user) {
        return svc.changeStatus(licenceId, "ACTIVE", body.reason(), user, null);
    }

    @PostMapping("/driving-licences/{licenceId}/renew")
    @Requires("licence.issue")
    @Operation(summary = "Extend expiry; an EXPIRED licence becomes ACTIVE again")
    public DrivingLicenceOut renew(@PathVariable Long licenceId, @Valid @RequestBody RenewRequest body, CurrentUser user) {
        return svc.renew(licenceId, body.newExpiryDate(), body.reason(), user);
    }

    @GetMapping("/driving-licences/{licenceId}/history")
    public List<LicenceStatusHistory> history(@PathVariable Long licenceId, CurrentUser user) {
        return svc.history(user, licenceId);
    }

    // ---- driving tests --------------------------------------------------------------------------------------

    @PostMapping("/driving-tests")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("licence.test")
    @Operation(summary = "Schedule a test (retakes add new rows; one pending test at a time)")
    public DrivingTest scheduleTest(@Valid @RequestBody TestCreate body, CurrentUser user) {
        return svc.scheduleTest(body, user);
    }

    @GetMapping("/driving-tests")
    @Requires("licence.view")
    public PageResponse<DrivingTest> tests(@RequestParam(name = "application_id", required = false) Long applicationId,
                                           @RequestParam(name = "citizen_id", required = false) Long citizenId,
                                           @RequestParam(required = false) @Pattern(regexp = "^[A-Z]+$") String result,
                                           @RequestParam(name = "test_centre_id", required = false) Long centreId, PageParams p) {
        return svc.tests(p, applicationId, citizenId, result, centreId);
    }

    @GetMapping("/driving-tests/{testId}")
    @Requires("licence.view")
    public DrivingTest test(@PathVariable Long testId) {
        return svc.test(testId);
    }

    @PatchMapping("/driving-tests/{testId}/result")
    @Requires("licence.test")
    public DrivingTest result(@PathVariable Long testId, @Valid @RequestBody TestResultIn body, CurrentUser user) {
        return svc.recordResult(testId, body.result(), body.remarks(), user);
    }
}
