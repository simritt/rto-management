package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/compliance")
public class ComplianceController {

    private final ApiClient api;
    private final UserSession session;

    public ComplianceController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    @GetMapping
    public String index(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("days", days);
        query.put("page", page);
        query.put("page_size", pageSize);
        if (kind != null && !kind.isBlank()) {
            query.put("kind", kind.trim());
        }

        JsonNode expiring = api.get("/compliance/expiring", query);
        JsonNode recentInspections = api.get("/inspections", Map.of("page", 1, "page_size", 10));

        model.addAttribute("data", expiring);
        model.addAttribute("days", days);
        model.addAttribute("kind", kind);
        model.addAttribute("inspections", recentInspections.path("items"));

        return "compliance/index";
    }

    @PostMapping("/refresh")
    public String refresh(RedirectAttributes ra) {
        requireAuth();

        try {
            JsonNode res = api.post("/compliance/refresh-statuses", Map.of());
            ra.addFlashAttribute("flash", "Compliance statuses refreshed: " + res.toString());
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to refresh statuses: " + e.getMessage());
        }

        return "redirect:/compliance";
    }

    @PostMapping("/inspections")
    public String createInspection(
            @RequestParam("vehicle_id") Long vehicleId,
            @RequestParam("result") String result,
            @RequestParam(required = false) String remarks,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicle_id", vehicleId);
        body.put("result", result.trim());
        if (remarks != null && !remarks.isBlank()) {
            body.put("remarks", remarks.trim());
        }

        try {
            api.post("/inspections", body);
            ra.addFlashAttribute("flash", "Vehicle inspection recorded successfully (" + result + ").");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to record inspection: " + e.getMessage());
        }

        return "redirect:/vehicles/" + vehicleId;
    }

    @PostMapping("/fitness")
    public String createFitness(
            @RequestParam("vehicle_id") Long vehicleId,
            @RequestParam(name = "inspection_id", required = false) Long inspectionId,
            @RequestParam("issue_date") String issueDate,
            @RequestParam("expiry_date") String expiryDate,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicle_id", vehicleId);
        if (inspectionId != null) {
            body.put("inspection_id", inspectionId);
        }
        body.put("issue_date", issueDate);
        body.put("expiry_date", expiryDate);

        try {
            api.post("/fitness-certificates", body);
            ra.addFlashAttribute("flash", "Fitness certificate issued successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to issue fitness certificate: " + e.getMessage());
        }

        return "redirect:/vehicles/" + vehicleId;
    }

    @PostMapping("/fitness/{id}/revoke")
    public String revokeFitness(
            @PathVariable long id,
            @RequestParam("vehicle_id") Long vehicleId,
            @RequestParam("reason") String reason,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason.trim());

        try {
            api.post("/fitness-certificates/" + id + "/revoke", body);
            ra.addFlashAttribute("flash", "Fitness certificate revoked.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to revoke certificate: " + e.getMessage());
        }

        return "redirect:/vehicles/" + vehicleId;
    }

    @PostMapping("/pollution")
    public String createPollution(
            @RequestParam("vehicle_id") Long vehicleId,
            @RequestParam("issue_date") String issueDate,
            @RequestParam("expiry_date") String expiryDate,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicle_id", vehicleId);
        body.put("issue_date", issueDate);
        body.put("expiry_date", expiryDate);

        try {
            api.post("/pollution-certificates", body);
            ra.addFlashAttribute("flash", "Pollution certificate (PUC) recorded successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to record pollution certificate: " + e.getMessage());
        }

        return "redirect:/vehicles/" + vehicleId;
    }

    @PostMapping("/insurance")
    public String createInsurance(
            @RequestParam("vehicle_id") Long vehicleId,
            @RequestParam("provider_name") String providerName,
            @RequestParam("policy_number") String policyNumber,
            @RequestParam("start_date") String startDate,
            @RequestParam("end_date") String endDate,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicle_id", vehicleId);
        body.put("provider_name", providerName.trim());
        body.put("policy_number", policyNumber.trim().toUpperCase());
        body.put("start_date", startDate);
        body.put("end_date", endDate);

        try {
            api.post("/insurance-policies", body);
            ra.addFlashAttribute("flash", "Insurance policy recorded successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to record insurance policy: " + e.getMessage());
        }

        return "redirect:/vehicles/" + vehicleId;
    }

    @PostMapping("/road-tax")
    public String createRoadTax(
            @RequestParam("vehicle_id") Long vehicleId,
            @RequestParam("assessment_year") Integer assessmentYear,
            @RequestParam("amount_due") BigDecimal amountDue,
            @RequestParam("due_date") String dueDate,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicle_id", vehicleId);
        body.put("assessment_year", assessmentYear);
        body.put("amount_due", amountDue);
        body.put("due_date", dueDate);

        try {
            api.post("/road-tax", body);
            ra.addFlashAttribute("flash", "Road tax assessed successfully (Rs " + amountDue + ").");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to assess road tax: " + e.getMessage());
        }

        return "redirect:/vehicles/" + vehicleId;
    }
}
