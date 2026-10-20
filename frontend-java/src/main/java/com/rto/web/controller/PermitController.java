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

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class PermitController {

    private final ApiClient api;
    private final UserSession session;

    public PermitController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    // =========================================================================
    // PERMITS
    // =========================================================================

    @GetMapping("/permits")
    public String listPermits(
            @RequestParam(required = false) String status,
            @RequestParam(name = "permit_type_id", required = false) Long permitTypeId,
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(name = "route_id", required = false) Long routeId,
            @RequestParam(name = "expiring_within_days", required = false) Integer expiring,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (permitTypeId != null) query.put("permit_type_id", permitTypeId);
        if (vehicleId != null) query.put("vehicle_id", vehicleId);
        if (citizenId != null) query.put("citizen_id", citizenId);
        if (routeId != null) query.put("route_id", routeId);
        if (expiring != null) query.put("expiring_within_days", expiring);

        JsonNode response = api.get("/permits", query);
        JsonNode permitTypes = api.get("/permit-types", Map.of("page", 1, "page_size", 100));

        model.addAttribute("data", response);
        model.addAttribute("permitTypes", permitTypes.path("items"));
        model.addAttribute("status", status);
        model.addAttribute("permitTypeId", permitTypeId);
        model.addAttribute("vehicleId", vehicleId);
        model.addAttribute("citizenId", citizenId);
        model.addAttribute("routeId", routeId);

        return "permits/list";
    }

    @GetMapping("/permits/new")
    public String newPermitForm(
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "application_id", required = false) Long applicationId,
            Model model) {
        requireAuth();

        loadPermitFormData(model);
        Map<String, Object> form = new LinkedHashMap<>();
        if (vehicleId != null) form.put("vehicle_id", vehicleId);
        if (applicationId != null) form.put("application_id", applicationId);
        model.addAttribute("form", form);

        return "permits/form";
    }

    @PostMapping("/permits")
    public String createPermit(@RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        if (params.get("vehicle_id") != null && !params.get("vehicle_id").isBlank()) {
            body.put("vehicle_id", Long.parseLong(params.get("vehicle_id")));
        }
        if (params.get("citizen_id") != null && !params.get("citizen_id").isBlank()) {
            body.put("citizen_id", Long.parseLong(params.get("citizen_id")));
        }
        if (params.get("permit_type_id") != null && !params.get("permit_type_id").isBlank()) {
            body.put("permit_type_id", Long.parseLong(params.get("permit_type_id")));
        }
        if (params.get("route_id") != null && !params.get("route_id").isBlank()) {
            body.put("route_id", Long.parseLong(params.get("route_id")));
        }
        if (params.get("application_id") != null && !params.get("application_id").isBlank()) {
            body.put("application_id", Long.parseLong(params.get("application_id")));
        }
        if (params.get("permit_number") != null && !params.get("permit_number").isBlank()) {
            body.put("permit_number", params.get("permit_number").trim().toUpperCase());
        }
        if (params.get("issue_date") != null && !params.get("issue_date").isBlank()) {
            body.put("issue_date", params.get("issue_date"));
        }
        if (params.get("expiry_date") != null && !params.get("expiry_date").isBlank()) {
            body.put("expiry_date", params.get("expiry_date"));
        }

        try {
            JsonNode created = api.post("/permits", body);
            ra.addFlashAttribute("flash", "Permit issued successfully.");
            return "redirect:/permits/" + created.path("permit_id").asLong();
        } catch (ApiException e) {
            loadPermitFormData(model);
            model.addAttribute("form", params);
            model.addAttribute("fieldErrors", Forms.fieldErrors(e.errors()));
            model.addAttribute("formError", e.getMessage());
            return "permits/form";
        }
    }

    @GetMapping("/permits/{id}")
    public String detailPermit(@PathVariable long id, Model model) {
        requireAuth();

        JsonNode permit = api.get("/permits/" + id);
        JsonNode history = api.get("/permits/" + id + "/history");
        JsonNode vehicle = api.get("/vehicles/" + permit.path("vehicle_id").asLong());

        model.addAttribute("permit", permit);
        model.addAttribute("history", history);
        model.addAttribute("vehicle", vehicle);

        return "permits/detail";
    }

    @PostMapping("/permits/{id}/suspend")
    public String suspendPermit(@PathVariable long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();

        try {
            api.post("/permits/" + id + "/suspend", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Permit suspended.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to suspend permit: " + e.getMessage());
        }

        return "redirect:/permits/" + id;
    }

    @PostMapping("/permits/{id}/reinstate")
    public String reinstatePermit(@PathVariable long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();

        try {
            api.post("/permits/" + id + "/reinstate", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Permit reinstated to ACTIVE.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to reinstate permit: " + e.getMessage());
        }

        return "redirect:/permits/" + id;
    }

    @PostMapping("/permits/{id}/cancel")
    public String cancelPermit(@PathVariable long id, @RequestParam String reason, RedirectAttributes ra) {
        requireAuth();

        try {
            api.post("/permits/" + id + "/cancel", Map.of("reason", reason.trim()));
            ra.addFlashAttribute("flash", "Permit cancelled.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to cancel permit: " + e.getMessage());
        }

        return "redirect:/permits/" + id;
    }

    @PostMapping("/permits/expire-overdue")
    public String expireOverdue(RedirectAttributes ra) {
        requireAuth();

        try {
            JsonNode res = api.post("/permits/expire-overdue", Map.of());
            ra.addFlashAttribute("flash", "Lapsed permits expired: " + res.toString());
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to expire overdue permits: " + e.getMessage());
        }

        return "redirect:/permits";
    }

    // =========================================================================
    // ROUTES & SEGMENTS
    // =========================================================================

    @GetMapping("/routes")
    public String listRoutes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        JsonNode response = api.get("/routes", Map.of("page", page, "page_size", pageSize));
        model.addAttribute("data", response);

        return "routes/list";
    }

    @GetMapping("/routes/new")
    public String newRouteForm(Model model) {
        requireAuth();
        model.addAttribute("form", Map.of());
        return "routes/form";
    }

    @PostMapping("/routes")
    public String createRoute(@RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        requireAuth();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("route_name", params.get("route_name") != null ? params.get("route_name").trim() : "");
        body.put("origin", params.get("origin") != null ? params.get("origin").trim() : "");
        body.put("destination", params.get("destination") != null ? params.get("destination").trim() : "");

        try {
            JsonNode created = api.post("/routes", body);
            ra.addFlashAttribute("flash", "Route " + created.path("route_name").asText() + " created successfully.");
            return "redirect:/routes/" + created.path("route_id").asLong();
        } catch (ApiException e) {
            model.addAttribute("form", params);
            model.addAttribute("fieldErrors", Forms.fieldErrors(e.errors()));
            model.addAttribute("formError", e.getMessage());
            return "routes/form";
        }
    }

    @GetMapping("/routes/{id}")
    public String detailRoute(@PathVariable long id, Model model) {
        requireAuth();

        JsonNode route = api.get("/routes/" + id);
        model.addAttribute("route", route);

        return "routes/detail";
    }

    @PostMapping("/routes/{id}/segments")
    public String addSegment(
            @PathVariable long id,
            @RequestParam("segment_name") String segmentName,
            @RequestParam(name = "sequence_no", required = false) Integer sequenceNo,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("segment_name", segmentName.trim());
        if (sequenceNo != null) {
            body.put("sequence_no", sequenceNo);
        }

        try {
            api.post("/routes/" + id + "/segments", body);
            ra.addFlashAttribute("flash", "Segment added to route.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to add segment: " + e.getMessage());
        }

        return "redirect:/routes/" + id;
    }

    private void loadPermitFormData(Model model) {
        JsonNode vehicles = api.get("/vehicles", Map.of("page", 1, "page_size", 100, "status", "ACTIVE"));
        JsonNode citizens = api.get("/citizens", Map.of("page", 1, "page_size", 100, "blacklisted", false));
        JsonNode permitTypes = api.get("/permit-types", Map.of("page", 1, "page_size", 100));
        JsonNode routes = api.get("/routes", Map.of("page", 1, "page_size", 100));
        JsonNode applications = api.get("/applications", Map.of("page", 1, "page_size", 100));

        model.addAttribute("vehicles", vehicles.path("items"));
        model.addAttribute("citizens", citizens.path("items"));
        model.addAttribute("permitTypes", permitTypes.path("items"));
        model.addAttribute("routes", routes.path("items"));
        model.addAttribute("applications", applications.path("items"));
    }
}
