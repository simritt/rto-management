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
@RequestMapping("/transfers")
public class OwnershipTransferController {

    private final ApiClient api;
    private final UserSession session;

    public OwnershipTransferController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) {
            query.put("status", status.trim());
        }
        if (vehicleId != null) {
            query.put("vehicle_id", vehicleId);
        }
        if (citizenId != null) {
            query.put("citizen_id", citizenId);
        }

        JsonNode response = api.get("/ownership-transfers", query);

        model.addAttribute("data", response);
        model.addAttribute("status", status);
        model.addAttribute("vehicleId", vehicleId);
        model.addAttribute("citizenId", citizenId);

        return "transfers/list";
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "application_id", required = false) Long applicationId,
            Model model) {
        requireAuth();

        loadFormData(model);
        Map<String, Object> form = new LinkedHashMap<>();
        if (vehicleId != null) form.put("vehicle_id", vehicleId);
        if (applicationId != null) form.put("application_id", applicationId);
        model.addAttribute("form", form);
        return "transfers/form";
    }

    @PostMapping
    public String create(@RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        if (params.get("vehicle_id") != null && !params.get("vehicle_id").isBlank()) {
            body.put("vehicle_id", Long.parseLong(params.get("vehicle_id")));
        }
        if (params.get("to_citizen_id") != null && !params.get("to_citizen_id").isBlank()) {
            body.put("to_citizen_id", Long.parseLong(params.get("to_citizen_id")));
        }
        if (params.get("from_citizen_id") != null && !params.get("from_citizen_id").isBlank()) {
            body.put("from_citizen_id", Long.parseLong(params.get("from_citizen_id")));
        }
        if (params.get("application_id") != null && !params.get("application_id").isBlank()) {
            body.put("application_id", Long.parseLong(params.get("application_id")));
        }

        try {
            JsonNode created = api.post("/ownership-transfers", body);
            ra.addFlashAttribute("flash", "Ownership transfer request created successfully.");
            return "redirect:/transfers/" + created.path("transfer_id").asLong();
        } catch (ApiException e) {
            loadFormData(model);
            model.addAttribute("form", params);
            model.addAttribute("fieldErrors", Forms.fieldErrors(e.errors()));
            model.addAttribute("formError", e.getMessage());
            return "transfers/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        requireAuth();

        JsonNode transfer = api.get("/ownership-transfers/" + id);
        long vehicleId = transfer.path("vehicle_id").asLong();
        JsonNode vehicle = api.get("/vehicles/" + vehicleId);

        model.addAttribute("transfer", transfer);
        model.addAttribute("vehicle", vehicle);

        return "transfers/detail";
    }

    @PostMapping("/{id}/approve")
    public String approve(
            @PathVariable long id,
            @RequestParam(name = "transfer_date", required = false) String transferDate,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        if (transferDate != null && !transferDate.isBlank()) {
            body.put("transfer_date", transferDate);
        }

        try {
            api.post("/ownership-transfers/" + id + "/approve", body);
            ra.addFlashAttribute("flash", "Ownership transfer approved successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to approve transfer: " + e.getMessage());
        }

        return "redirect:/transfers/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(
            @PathVariable long id,
            @RequestParam String reason,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason.trim());

        try {
            api.post("/ownership-transfers/" + id + "/reject", body);
            ra.addFlashAttribute("flash", "Ownership transfer rejected.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to reject transfer: " + e.getMessage());
        }

        return "redirect:/transfers/" + id;
    }

    private void loadFormData(Model model) {
        JsonNode vehicles = api.get("/vehicles", Map.of("page", 1, "page_size", 100, "status", "ACTIVE"));
        JsonNode citizens = api.get("/citizens", Map.of("page", 1, "page_size", 100, "blacklisted", false));
        JsonNode applications = api.get("/applications", Map.of("page", 1, "page_size", 100));

        model.addAttribute("vehicles", vehicles.path("items"));
        model.addAttribute("citizens", citizens.path("items"));
        model.addAttribute("applications", applications.path("items"));
    }
}
