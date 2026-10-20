package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import com.rto.web.util.Forms;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class LicenceController {

    private final ApiClient api;
    private final UserSession session;

    public LicenceController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    // =========================================================================
    // 1. DRIVING LICENCES (/licences)
    // =========================================================================

    @GetMapping("/licences")
    public String listLicences(
            @RequestParam(required = false) String status,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(name = "office_id", required = false) Long officeId,
            @RequestParam(name = "class_id", required = false) Long classId,
            @RequestParam(name = "expiring_within_days", required = false) Integer expiring,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (citizenId != null) query.put("citizen_id", citizenId);
        if (officeId != null) query.put("office_id", officeId);
        if (classId != null) query.put("class_id", classId);
        if (expiring != null) query.put("expiring_within_days", expiring);
        if (search != null && !search.isBlank()) query.put("search", search.trim());

        JsonNode response = api.get("/driving-licences", query);
        model.addAttribute("data", response);

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("licenceClasses", api.get("/licence-classes").path("items"));
        } catch (Exception ignored) {}

        model.addAttribute("paramStatus", status);
        model.addAttribute("paramCitizenId", citizenId);
        model.addAttribute("paramOfficeId", officeId);
        model.addAttribute("paramClassId", classId);
        model.addAttribute("paramExpiring", expiring);
        model.addAttribute("paramSearch", search);

        return "licences/list";
    }

    @GetMapping("/licences/new")
    public String newLicenceForm(
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(name = "application_id", required = false) Long applicationId,
            @RequestParam(name = "learner_id", required = false) Long learnerId,
            Model model) {
        requireAuth();

        model.addAttribute("preCitizenId", citizenId);
        model.addAttribute("preApplicationId", applicationId);
        model.addAttribute("preLearnerId", learnerId);

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("licenceClasses", api.get("/licence-classes").path("items"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("citizens", api.get("/citizens", Map.of("page_size", 100)).path("items"));
        } catch (Exception ignored) {}

        return "licences/form";
    }

    @PostMapping("/licences")
    public String createLicence(
            @RequestParam Map<String, String> params,
            @RequestParam(name = "licence_class_ids", required = false) List<Long> licenceClassIds,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("citizen_id", Long.parseLong(params.get("citizen_id")));
            body.put("application_id", Long.parseLong(params.get("application_id")));
            body.put("office_id", Long.parseLong(params.get("office_id")));

            String learnerId = params.get("learner_licence_id");
            if (learnerId != null && !learnerId.isBlank()) {
                body.put("learner_licence_id", Long.parseLong(learnerId.trim()));
            }

            if (licenceClassIds == null || licenceClassIds.isEmpty()) {
                ra.addFlashAttribute("flashError", "At least one licence class must be selected");
                return "redirect:/licences/new";
            }
            body.put("licence_class_ids", licenceClassIds);

            String licNum = params.get("licence_number");
            if (licNum != null && !licNum.isBlank()) {
                body.put("licence_number", licNum.trim());
            }
            String issueDate = params.get("issue_date");
            if (issueDate != null && !issueDate.isBlank()) {
                body.put("issue_date", issueDate.trim());
            }
            String expiryDate = params.get("expiry_date");
            if (expiryDate != null && !expiryDate.isBlank()) {
                body.put("expiry_date", expiryDate.trim());
            }

            JsonNode created = api.post("/driving-licences", body);
            long id = created.path("driving_licence_id").asLong();
            ra.addFlashAttribute("flash", "Driving licence issued successfully (#" + created.path("licence_number").asText() + ")");
            return "redirect:/licences/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/licences/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form input: " + e.getMessage());
            return "redirect:/licences/new";
        }
    }

    @GetMapping("/licences/{id}")
    public String viewLicence(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode licence = api.get("/driving-licences/" + id);
        model.addAttribute("licence", licence);

        long citizenId = licence.path("citizen_id").asLong();
        try {
            model.addAttribute("citizen", api.get("/citizens/" + citizenId));
        } catch (Exception ignored) {}

        long officeId = licence.path("office_id").asLong();
        try {
            model.addAttribute("office", api.get("/offices/" + officeId));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("history", api.get("/driving-licences/" + id + "/history"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("allClasses", api.get("/licence-classes").path("items"));
        } catch (Exception ignored) {}

        return "licences/detail";
    }

    @PostMapping("/licences/{id}/classes")
    public String addLicenceClass(@PathVariable Long id, @RequestParam("licence_class_id") Long classId, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/driving-licences/" + id + "/classes", Map.of("licence_class_id", classId));
            ra.addFlashAttribute("flash", "Endorsement class added successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/licences/" + id;
    }

    @PostMapping("/licences/{id}/suspend")
    public String suspendLicence(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/driving-licences/" + id + "/suspend", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Licence suspended successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/licences/" + id;
    }

    @PostMapping("/licences/{id}/reinstate")
    public String reinstateLicence(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/driving-licences/" + id + "/reinstate", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Licence reinstated to ACTIVE status.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/licences/" + id;
    }

    @PostMapping("/licences/{id}/revoke")
    public String revokeLicence(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/driving-licences/" + id + "/revoke", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Licence revoked permanently.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/licences/" + id;
    }

    @PostMapping("/licences/{id}/renew")
    public String renewLicence(@PathVariable Long id,
                               @RequestParam("new_expiry_date") String newExpiryDate,
                               @RequestParam(required = false, defaultValue = "Licence renewed") String reason,
                               RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/driving-licences/" + id + "/renew", Map.of("new_expiry_date", newExpiryDate.trim(), "reason", reason.trim()));
            ra.addFlashAttribute("flash", "Licence renewed successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/licences/" + id;
    }

    @PostMapping("/licences/expire-overdue")
    public String expireOverdue(RedirectAttributes ra) {
        requireAuth();
        try {
            JsonNode res = api.post("/driving-licences/expire-overdue", Map.of());
            int dl = res.path("driving_licences_expired").asInt();
            int ll = res.path("learner_licences_expired").asInt();
            ra.addFlashAttribute("flash", "Overdue sweep completed: " + dl + " driving licence(s) and " + ll + " learner licence(s) expired.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/licences";
    }

    // =========================================================================
    // 2. LEARNER LICENCES (/learner-licences)
    // =========================================================================

    @GetMapping("/learner-licences")
    public String listLearners(
            @RequestParam(required = false) String status,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(name = "office_id", required = false) Long officeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (citizenId != null) query.put("citizen_id", citizenId);
        if (officeId != null) query.put("office_id", officeId);
        if (search != null && !search.isBlank()) query.put("search", search.trim());

        JsonNode response = api.get("/learner-licences", query);
        model.addAttribute("data", response);

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}

        model.addAttribute("paramStatus", status);
        model.addAttribute("paramCitizenId", citizenId);
        model.addAttribute("paramOfficeId", officeId);
        model.addAttribute("paramSearch", search);

        return "learners/list";
    }

    @GetMapping("/learner-licences/new")
    public String newLearnerForm(
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(name = "application_id", required = false) Long applicationId,
            Model model) {
        requireAuth();

        model.addAttribute("preCitizenId", citizenId);
        model.addAttribute("preApplicationId", applicationId);

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("citizens", api.get("/citizens", Map.of("page_size", 100)).path("items"));
        } catch (Exception ignored) {}

        return "learners/form";
    }

    @PostMapping("/learner-licences")
    public String createLearner(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("citizen_id", Long.parseLong(params.get("citizen_id")));
            body.put("application_id", Long.parseLong(params.get("application_id")));
            body.put("office_id", Long.parseLong(params.get("office_id")));

            String licNum = params.get("licence_number");
            if (licNum != null && !licNum.isBlank()) {
                body.put("licence_number", licNum.trim());
            }
            String issueDate = params.get("issue_date");
            if (issueDate != null && !issueDate.isBlank()) {
                body.put("issue_date", issueDate.trim());
            }
            String expiryDate = params.get("expiry_date");
            if (expiryDate != null && !expiryDate.isBlank()) {
                body.put("expiry_date", expiryDate.trim());
            }

            JsonNode created = api.post("/learner-licences", body);
            long id = created.path("learner_licence_id").asLong();
            ra.addFlashAttribute("flash", "Learner licence issued successfully (#" + created.path("licence_number").asText() + ")");
            return "redirect:/learner-licences/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/learner-licences/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form input: " + e.getMessage());
            return "redirect:/learner-licences/new";
        }
    }

    @GetMapping("/learner-licences/{id}")
    public String viewLearner(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode learner = api.get("/learner-licences/" + id);
        model.addAttribute("learner", learner);

        long citizenId = learner.path("citizen_id").asLong();
        try {
            model.addAttribute("citizen", api.get("/citizens/" + citizenId));
        } catch (Exception ignored) {}

        long officeId = learner.path("office_id").asLong();
        try {
            model.addAttribute("office", api.get("/offices/" + officeId));
        } catch (Exception ignored) {}

        return "learners/detail";
    }

    @PostMapping("/learner-licences/{id}/cancel")
    public String cancelLearner(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/learner-licences/" + id + "/cancel", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Learner licence cancelled.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/learner-licences/" + id;
    }

    // =========================================================================
    // 3. DRIVING TESTS (/driving-tests)
    // =========================================================================

    @GetMapping("/driving-tests")
    public String listTests(
            @RequestParam(name = "application_id", required = false) Long applicationId,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(required = false) String result,
            @RequestParam(name = "test_centre_id", required = false) Long testCentreId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (applicationId != null) query.put("application_id", applicationId);
        if (citizenId != null) query.put("citizen_id", citizenId);
        if (result != null && !result.isBlank()) query.put("result", result.trim());
        if (testCentreId != null) query.put("test_centre_id", testCentreId);

        JsonNode response = api.get("/driving-tests", query);
        model.addAttribute("data", response);

        try {
            model.addAttribute("testCentres", api.get("/test-centres"));
        } catch (Exception ignored) {}

        model.addAttribute("paramApplicationId", applicationId);
        model.addAttribute("paramCitizenId", citizenId);
        model.addAttribute("paramResult", result);
        model.addAttribute("paramTestCentreId", testCentreId);

        return "tests/list";
    }

    @GetMapping("/driving-tests/new")
    public String newTestForm(
            @RequestParam(name = "application_id", required = false) Long applicationId,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            Model model) {
        requireAuth();

        model.addAttribute("preApplicationId", applicationId);
        model.addAttribute("preCitizenId", citizenId);

        try {
            model.addAttribute("testCentres", api.get("/test-centres"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("citizens", api.get("/citizens", Map.of("page_size", 100)).path("items"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("employees", api.get("/employees", Map.of("is_active", true, "page_size", 100)).path("items"));
        } catch (Exception ignored) {}

        return "tests/form";
    }

    @PostMapping("/driving-tests")
    public String scheduleTest(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("application_id", Long.parseLong(params.get("application_id")));
            body.put("citizen_id", Long.parseLong(params.get("citizen_id")));
            body.put("test_centre_id", Long.parseLong(params.get("test_centre_id")));
            body.put("examiner_employee_id", Long.parseLong(params.get("examiner_employee_id")));

            String scheduledAt = params.get("scheduled_at");
            if (scheduledAt != null && !scheduledAt.isBlank()) {
                // If HTML datetime-local format YYYY-MM-DDTHH:mm, ensure seconds
                if (scheduledAt.length() == 16) {
                    scheduledAt = scheduledAt + ":00";
                }
                body.put("scheduled_at", scheduledAt);
            }

            String remarks = params.get("remarks");
            if (remarks != null && !remarks.isBlank()) {
                body.put("remarks", remarks.trim());
            }

            JsonNode created = api.post("/driving-tests", body);
            long id = created.path("test_id").asLong();
            ra.addFlashAttribute("flash", "Driving test scheduled successfully for candidate.");
            return "redirect:/driving-tests/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/driving-tests/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form input: " + e.getMessage());
            return "redirect:/driving-tests/new";
        }
    }

    @GetMapping("/driving-tests/{id}")
    public String viewTest(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode test = api.get("/driving-tests/" + id);
        model.addAttribute("test", test);

        long citizenId = test.path("citizen_id").asLong();
        try {
            model.addAttribute("citizen", api.get("/citizens/" + citizenId));
        } catch (Exception ignored) {}

        long centreId = test.path("test_centre_id").asLong();
        try {
            // Find centre in list
            JsonNode centres = api.get("/test-centres");
            if (centres.isArray()) {
                for (JsonNode c : centres) {
                    if (c.path("test_centre_id").asLong() == centreId) {
                        model.addAttribute("centre", c);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        long examinerId = test.path("examiner_employee_id").asLong();
        try {
            model.addAttribute("examiner", api.get("/employees/" + examinerId));
        } catch (Exception ignored) {}

        return "tests/detail";
    }

    @PostMapping("/driving-tests/{id}/result")
    public String recordTestResult(
            @PathVariable Long id,
            @RequestParam String result,
            @RequestParam(required = false) String remarks,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("result", result.trim());
            if (remarks != null && !remarks.isBlank()) {
                body.put("remarks", remarks.trim());
            }
            api.patch("/driving-tests/" + id + "/result", body);
            ra.addFlashAttribute("flash", "Driving test result recorded: " + result);
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/driving-tests/" + id;
    }

    // =========================================================================
    // 4. DRIVING SCHOOLS & TEST CENTRES (/driving-schools)
    // =========================================================================

    @GetMapping("/driving-schools")
    public String listSchools(
            @RequestParam(name = "office_id", required = false) Long officeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (officeId != null) query.put("office_id", officeId);
        if (search != null && !search.isBlank()) query.put("search", search.trim());

        JsonNode response = api.get("/driving-schools", query);
        model.addAttribute("data", response);

        try {
            model.addAttribute("testCentres", api.get("/test-centres"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}

        model.addAttribute("paramOfficeId", officeId);
        model.addAttribute("paramSearch", search);

        return "schools/list";
    }

    @PostMapping("/driving-schools")
    public String createSchool(
            @RequestParam("school_name") String schoolName,
            @RequestParam("licence_number") String licenceNumber,
            @RequestParam("office_id") Long officeId,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = Map.of(
                    "school_name", schoolName.trim(),
                    "licence_number", licenceNumber.trim(),
                    "office_id", officeId
            );
            JsonNode created = api.post("/driving-schools", body);
            long id = created.path("school_id").asLong();
            ra.addFlashAttribute("flash", "Driving school registered successfully.");
            return "redirect:/driving-schools/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/driving-schools";
        }
    }

    @GetMapping("/driving-schools/{id}")
    public String viewSchool(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode school = api.get("/driving-schools/" + id);
        model.addAttribute("school", school);

        long officeId = school.path("office_id").asLong();
        try {
            model.addAttribute("office", api.get("/offices/" + officeId));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("instructors", api.get("/driving-schools/" + id + "/instructors"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("licenceClasses", api.get("/licence-classes").path("items"));
        } catch (Exception ignored) {}

        return "schools/detail";
    }

    @PostMapping("/driving-schools/{id}/instructors")
    public String addInstructor(
            @PathVariable Long id,
            @RequestParam(name = "person_id", required = false) Long personId,
            @RequestParam("licence_class_id") Long licenceClassId,
            @RequestParam(name = "first_name", required = false) String firstName,
            @RequestParam(name = "last_name", required = false) String lastName,
            @RequestParam(name = "date_of_birth", required = false) String dob,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "national_id_number", required = false) String nationalIdNumber,
            @RequestParam(name = "phone_primary", required = false) String phonePrimary,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "email", required = false) String email,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("licence_class_id", licenceClassId);

            if (personId != null) {
                body.put("person_id", personId);
            } else if (firstName != null && !firstName.isBlank()) {
                Map<String, Object> person = new LinkedHashMap<>();
                person.put("first_name", firstName.trim());
                person.put("last_name", lastName != null ? lastName.trim() : "");
                if (dob != null && !dob.isBlank()) person.put("date_of_birth", dob.trim());
                if (gender != null && !gender.isBlank()) person.put("gender", gender.trim());
                String ph = phonePrimary != null && !phonePrimary.isBlank() ? phonePrimary.trim() : (phone != null ? phone.trim() : "");
                person.put("phone_primary", ph);
                String nid = nationalIdNumber != null && !nationalIdNumber.isBlank() ? nationalIdNumber.trim() : ("NAT-" + System.currentTimeMillis() % 100000000L);
                person.put("national_id_number", nid);
                if (email != null && !email.isBlank()) person.put("email", email.trim());
                body.put("person", person);
            } else {
                ra.addFlashAttribute("flashError", "Either person ID or person details must be provided");
                return "redirect:/driving-schools/" + id;
            }

            api.post("/driving-schools/" + id + "/instructors", body);
            ra.addFlashAttribute("flash", "Instructor added successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/driving-schools/" + id;
    }

    @PostMapping("/test-centres")
    public String createTestCentre(
            @RequestParam("office_id") Long officeId,
            @RequestParam("centre_name") String centreName,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.post("/test-centres", Map.of("office_id", officeId, "centre_name", centreName.trim()));
            ra.addFlashAttribute("flash", "Test centre registered successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/driving-schools";
    }
}
