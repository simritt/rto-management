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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/vehicles")
public class VehicleController {

    private final ApiClient api;
    private final UserSession session;

    public VehicleController(ApiClient api, UserSession session) {
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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(name = "vehicle_type_id", required = false) Long vehicleTypeId,
            @RequestParam(name = "fuel_type_id", required = false) Long fuelTypeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (search != null && !search.isBlank()) {
            query.put("registration_number", search.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            query.put("status", status.trim());
        }
        if (vehicleTypeId != null) {
            query.put("vehicle_type_id", vehicleTypeId);
        }
        if (fuelTypeId != null) {
            query.put("fuel_type_id", fuelTypeId);
        }

        JsonNode response = api.get("/vehicles", query);
        JsonNode vehicleTypes = api.get("/vehicle-types", Map.of("page", 1, "page_size", 100));
        JsonNode fuelTypes = api.get("/fuel-types", Map.of("page", 1, "page_size", 100));

        model.addAttribute("data", response);
        model.addAttribute("vehicleTypes", vehicleTypes.path("items"));
        model.addAttribute("fuelTypes", fuelTypes.path("items"));
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("vehicleTypeId", vehicleTypeId);
        model.addAttribute("fuelTypeId", fuelTypeId);

        return "vehicles/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        requireAuth();

        loadFormData(model);
        model.addAttribute("form", Map.of("manufacture_year", LocalDate.now().getYear()));
        return "vehicles/form";
    }

    @PostMapping
    public String create(@RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registration_number", params.get("registration_number") != null ? params.get("registration_number").trim().toUpperCase() : null);
        body.put("chassis_number", params.get("chassis_number") != null ? params.get("chassis_number").trim().toUpperCase() : null);
        body.put("engine_number", params.get("engine_number") != null ? params.get("engine_number").trim().toUpperCase() : null);

        if (params.get("manufacturer_id") != null && !params.get("manufacturer_id").isBlank()) {
            body.put("manufacturer_id", Long.parseLong(params.get("manufacturer_id")));
        }
        if (params.get("model_id") != null && !params.get("model_id").isBlank()) {
            body.put("model_id", Long.parseLong(params.get("model_id")));
        }
        if (params.get("vehicle_type_id") != null && !params.get("vehicle_type_id").isBlank()) {
            body.put("vehicle_type_id", Long.parseLong(params.get("vehicle_type_id")));
        }
        if (params.get("fuel_type_id") != null && !params.get("fuel_type_id").isBlank()) {
            body.put("fuel_type_id", Long.parseLong(params.get("fuel_type_id")));
        }
        if (params.get("manufacture_year") != null && !params.get("manufacture_year").isBlank()) {
            body.put("manufacture_year", Integer.parseInt(params.get("manufacture_year")));
        }
        if (params.get("color") != null && !params.get("color").isBlank()) {
            body.put("color", params.get("color").trim());
        }
        if (params.get("registering_office_id") != null && !params.get("registering_office_id").isBlank()) {
            body.put("registering_office_id", Long.parseLong(params.get("registering_office_id")));
        }
        if (params.get("registration_date") != null && !params.get("registration_date").isBlank()) {
            body.put("registration_date", params.get("registration_date"));
        }
        if (params.get("owner_citizen_id") != null && !params.get("owner_citizen_id").isBlank()) {
            body.put("owner_citizen_id", Long.parseLong(params.get("owner_citizen_id")));
        }

        try {
            JsonNode created = api.post("/vehicles", body);
            ra.addFlashAttribute("flash", "Vehicle " + created.path("registration_number").asText() + " registered successfully.");
            return "redirect:/vehicles/" + created.path("vehicle_id").asLong();
        } catch (ApiException e) {
            loadFormData(model);
            model.addAttribute("form", params);
            model.addAttribute("fieldErrors", Forms.fieldErrors(e.errors()));
            model.addAttribute("formError", e.getMessage());
            return "vehicles/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        requireAuth();

        JsonNode vehicle = api.get("/vehicles/" + id);
        JsonNode owners = api.get("/vehicles/" + id + "/owners");
        JsonNode compliance = api.get("/vehicles/" + id + "/compliance");
        JsonNode citizens = api.get("/citizens", Map.of("page", 1, "page_size", 50, "blacklisted", false));

        model.addAttribute("vehicle", vehicle);
        model.addAttribute("owners", owners);
        model.addAttribute("compliance", compliance);
        model.addAttribute("citizens", citizens.path("items"));

        return "vehicles/detail";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable long id,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String status,
            @RequestParam String reason,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, String> body = new LinkedHashMap<>();
        if (color != null && !color.isBlank()) {
            body.put("color", color.trim());
        }
        if (status != null && !status.isBlank()) {
            body.put("status", status.trim());
        }
        body.put("reason", reason.trim());

        try {
            api.patch("/vehicles/" + id, body);
            ra.addFlashAttribute("flash", "Vehicle details updated successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to update vehicle: " + e.getMessage());
        }

        return "redirect:/vehicles/" + id;
    }

    @PostMapping("/{id}/owners")
    public String addOwner(
            @PathVariable long id,
            @RequestParam("citizen_id") Long citizenId,
            @RequestParam(name = "effective_from", required = false) String effectiveFrom,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("citizen_id", citizenId);
        if (effectiveFrom != null && !effectiveFrom.isBlank()) {
            body.put("effective_from", effectiveFrom);
        }

        try {
            api.post("/vehicles/" + id + "/owners", body);
            ra.addFlashAttribute("flash", "Owner recorded successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to add owner: " + e.getMessage());
        }

        return "redirect:/vehicles/" + id;
    }

    private void loadFormData(Model model) {
        JsonNode manufacturers = api.get("/vehicle-manufacturers", Map.of("page", 1, "page_size", 100));
        JsonNode models = api.get("/vehicle-models", Map.of("page", 1, "page_size", 100));
        JsonNode vehicleTypes = api.get("/vehicle-types", Map.of("page", 1, "page_size", 100));
        JsonNode fuelTypes = api.get("/fuel-types", Map.of("page", 1, "page_size", 100));
        JsonNode offices = api.get("/offices", Map.of("page", 1, "page_size", 100, "is_active", true));
        JsonNode citizens = api.get("/citizens", Map.of("page", 1, "page_size", 50, "blacklisted", false));

        model.addAttribute("manufacturers", manufacturers.path("items"));
        model.addAttribute("models", models.path("items"));
        model.addAttribute("vehicleTypes", vehicleTypes.path("items"));
        model.addAttribute("fuelTypes", fuelTypes.path("items"));
        model.addAttribute("offices", offices.path("items"));
        model.addAttribute("citizens", citizens.path("items"));
    }
}
