package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class ViolationController {

    private final ApiClient api;
    private final UserSession session;

    public ViolationController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    // =========================================================================
    // 1. VIOLATIONS (/violations)
    // =========================================================================

    @GetMapping("/violations")
    public String listViolations(
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "driver_citizen_id", required = false) Long driverCitizenId,
            @RequestParam(name = "violation_type_id", required = false) Long violationTypeId,
            @RequestParam(name = "officer_id", required = false) Long officerId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(required = false) Boolean unchallaned,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (vehicleId != null) query.put("vehicle_id", vehicleId);
        if (driverCitizenId != null) query.put("driver_citizen_id", driverCitizenId);
        if (violationTypeId != null) query.put("violation_type_id", violationTypeId);
        if (officerId != null) query.put("officer_id", officerId);
        if (dateFrom != null && !dateFrom.isBlank()) query.put("date_from", dateFrom.trim());
        if (dateTo != null && !dateTo.isBlank()) query.put("date_to", dateTo.trim());
        if (unchallaned != null) query.put("unchallaned", unchallaned);
        if (search != null && !search.isBlank()) query.put("search", search.trim());

        JsonNode response = api.get("/violations", query);
        model.addAttribute("data", response);

        try {
            model.addAttribute("violationTypes", api.get("/violation-types").path("items"));
        } catch (Exception ignored) {}

        model.addAttribute("paramVehicleId", vehicleId);
        model.addAttribute("paramDriverCitizenId", driverCitizenId);
        model.addAttribute("paramViolationTypeId", violationTypeId);
        model.addAttribute("paramOfficerId", officerId);
        model.addAttribute("paramDateFrom", dateFrom);
        model.addAttribute("paramDateTo", dateTo);
        model.addAttribute("paramUnchallaned", unchallaned);
        model.addAttribute("paramSearch", search);

        return "violations/list";
    }

    @GetMapping("/violations/new")
    public String newViolationForm(
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "driver_citizen_id", required = false) Long driverCitizenId,
            Model model) {
        requireAuth();

        model.addAttribute("preVehicleId", vehicleId);
        model.addAttribute("preDriverCitizenId", driverCitizenId);

        try {
            model.addAttribute("violationTypes", api.get("/violation-types").path("items"));
        } catch (Exception ignored) {}
        try {
            model.addAttribute("officers", api.get("/employees", Map.of("is_active", true, "page_size", 100)).path("items"));
        } catch (Exception ignored) {}

        return "violations/form";
    }

    @PostMapping("/violations")
    public String createViolation(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vehicle_id", Long.parseLong(params.get("vehicle_id")));
            body.put("violation_type_id", Long.parseLong(params.get("violation_type_id")));
            body.put("location", params.get("location").trim());

            String driverCitizenId = params.get("driver_citizen_id");
            if (driverCitizenId != null && !driverCitizenId.isBlank()) {
                body.put("driver_citizen_id", Long.parseLong(driverCitizenId.trim()));
            }

            String officerId = params.get("officer_employee_id");
            if (officerId != null && !officerId.isBlank()) {
                body.put("officer_employee_id", Long.parseLong(officerId.trim()));
            }

            String occurredAt = params.get("occurred_at");
            if (occurredAt != null && !occurredAt.isBlank()) {
                if (occurredAt.length() == 16) {
                    occurredAt = occurredAt + ":00";
                }
                body.put("occurred_at", occurredAt);
            }

            JsonNode created = api.post("/violations", body);
            long id = created.path("violation_id").asLong();
            ra.addFlashAttribute("flash", "Traffic violation logged successfully (#" + id + ")");
            return "redirect:/violations/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/violations/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form input: " + e.getMessage());
            return "redirect:/violations/new";
        }
    }

    @GetMapping("/violations/{id}")
    public String viewViolation(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode violation = api.get("/violations/" + id);
        model.addAttribute("violation", violation);

        long vehicleId = violation.path("vehicle_id").asLong();
        try {
            model.addAttribute("vehicle", api.get("/vehicles/" + vehicleId));
        } catch (Exception ignored) {}

        if (!violation.path("driver_citizen_id").isNull()) {
            long driverId = violation.path("driver_citizen_id").asLong();
            try {
                model.addAttribute("driver", api.get("/citizens/" + driverId));
            } catch (Exception ignored) {}
        }

        if (!violation.path("officer_employee_id").isNull()) {
            long officerId = violation.path("officer_employee_id").asLong();
            try {
                model.addAttribute("officer", api.get("/employees/" + officerId));
            } catch (Exception ignored) {}
        }

        return "violations/detail";
    }

    // =========================================================================
    // 2. CHALLANS (/challans)
    // =========================================================================

    @GetMapping("/challans")
    public String listChallans(
            @RequestParam(required = false) String status,
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "driver_citizen_id", required = false) Long driverCitizenId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (vehicleId != null) query.put("vehicle_id", vehicleId);
        if (driverCitizenId != null) query.put("driver_citizen_id", driverCitizenId);
        if (dateFrom != null && !dateFrom.isBlank()) query.put("date_from", dateFrom.trim());
        if (dateTo != null && !dateTo.isBlank()) query.put("date_to", dateTo.trim());

        JsonNode response = api.get("/challans", query);
        model.addAttribute("data", response);

        model.addAttribute("paramStatus", status);
        model.addAttribute("paramVehicleId", vehicleId);
        model.addAttribute("paramDriverCitizenId", driverCitizenId);
        model.addAttribute("paramDateFrom", dateFrom);
        model.addAttribute("paramDateTo", dateTo);

        return "challans/list";
    }

    @GetMapping("/challans/new")
    public String newChallanForm(
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            Model model) {
        requireAuth();

        model.addAttribute("selectedVehicleId", vehicleId);

        if (vehicleId != null) {
            try {
                JsonNode unchallaned = api.get("/violations", Map.of("vehicle_id", vehicleId, "unchallaned", true, "page_size", 100));
                model.addAttribute("availableViolations", unchallaned.path("items"));
                model.addAttribute("vehicle", api.get("/vehicles/" + vehicleId));
            } catch (Exception ignored) {}
        } else {
            try {
                JsonNode unchallaned = api.get("/violations", Map.of("unchallaned", true, "page_size", 100));
                model.addAttribute("availableViolations", unchallaned.path("items"));
            } catch (Exception ignored) {}
        }

        return "challans/form";
    }

    @PostMapping("/challans")
    public String createChallan(
            @RequestParam(name = "violation_ids", required = false) List<Long> violationIds,
            @RequestParam(name = "challan_number", required = false) String challanNumber,
            RedirectAttributes ra) {
        requireAuth();

        if (violationIds == null || violationIds.isEmpty()) {
            ra.addFlashAttribute("flashError", "Select at least one violation to bundle into the challan");
            return "redirect:/challans/new";
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("violation_ids", violationIds);
            if (challanNumber != null && !challanNumber.isBlank()) {
                body.put("challan_number", challanNumber.trim());
            }

            JsonNode created = api.post("/challans", body);
            long id = created.path("challan_id").asLong();
            ra.addFlashAttribute("flash", "Challan issued successfully (#" + created.path("challan_number").asText() + ")");
            return "redirect:/challans/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/challans/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Error creating challan: " + e.getMessage());
            return "redirect:/challans/new";
        }
    }

    @GetMapping("/challans/{id}")
    public String viewChallan(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode challan = api.get("/challans/" + id);
        model.addAttribute("challan", challan);

        try {
            model.addAttribute("history", api.get("/challans/" + id + "/history"));
        } catch (Exception ignored) {}

        long vehicleId = challan.path("vehicle_id").asLong();
        try {
            model.addAttribute("vehicle", api.get("/vehicles/" + vehicleId));
            // Also fetch unchallaned violations for this vehicle to allow adding them
            JsonNode extra = api.get("/violations", Map.of("vehicle_id", vehicleId, "unchallaned", true, "page_size", 50));
            model.addAttribute("availableViolations", extra.path("items"));
        } catch (Exception ignored) {}

        return "challans/detail";
    }

    @PostMapping("/challans/{id}/violations")
    public String addViolationsToChallan(
            @PathVariable Long id,
            @RequestParam(name = "violation_ids", required = false) List<Long> violationIds,
            RedirectAttributes ra) {
        requireAuth();

        if (violationIds == null || violationIds.isEmpty()) {
            ra.addFlashAttribute("flashError", "Select at least one violation to attach");
            return "redirect:/challans/" + id;
        }

        try {
            api.post("/challans/" + id + "/violations", Map.of("violation_ids", violationIds));
            ra.addFlashAttribute("flash", "Violations attached to challan successfully. Total recalculated.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/challans/" + id;
    }

    @PostMapping("/challans/{id}/dispute")
    public String disputeChallan(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/challans/" + id + "/dispute", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Challan marked as DISPUTED.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/challans/" + id;
    }

    @PostMapping("/challans/{id}/reissue")
    public String reissueChallan(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/challans/" + id + "/reissue", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Dispute dismissed. Challan re-issued.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/challans/" + id;
    }

    @PostMapping("/challans/{id}/cancel")
    public String cancelChallan(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/challans/" + id + "/cancel", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Challan cancelled. Violations released.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/challans/" + id;
    }

    @PostMapping("/challans/{id}/pay")
    public String payChallan(
            @PathVariable Long id,
            @RequestParam(name = "gateway_reference", required = false) String gatewayRef,
            @RequestParam(name = "outcome", defaultValue = "SUCCESS") String outcome,
            RedirectAttributes ra) {
        requireAuth();

        try {
            String ref = (gatewayRef != null && !gatewayRef.isBlank()) ? gatewayRef.trim() : "GATEWAY-CH-" + System.currentTimeMillis();
            api.post("/challans/" + id + "/pay", Map.of("gateway_reference", ref, "outcome", outcome));
            ra.addFlashAttribute("flash", "Payment processed successfully. Challan marked as PAID.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/challans/" + id;
    }
}
