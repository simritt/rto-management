package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import com.rto.web.util.Paging;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/slots")
public class AppointmentSlotsController {

    private final ApiClient api;
    private final UserSession session;

    public AppointmentSlotsController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Long office_id,
            @RequestParam(required = false) String date_from,
            @RequestParam(required = false) String date_to,
            Model model) {
        requireManage();

        Map<String, Object> filters = new LinkedHashMap<>();
        if (office_id != null) filters.put("office_id", office_id);
        if (date_from != null && !date_from.isBlank()) filters.put("date_from", date_from);
        if (date_to != null && !date_to.isBlank()) filters.put("date_to", date_to);

        Map<String, Object> query = new LinkedHashMap<>(filters);
        query.put("page", page);
        query.put("page_size", 20);

        JsonNode result = api.get("/appointment-slots", query);
        JsonNode offices = api.get("/offices", Map.of("page", 1, "page_size", 100));

        model.addAttribute("result", result);
        model.addAttribute("offices", offices.path("items"));
        model.addAttribute("office_id", office_id);
        model.addAttribute("date_from", date_from);
        model.addAttribute("date_to", date_to);
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("pagerBase", Paging.base("/admin/slots", filters));

        return "admin/slots/list";
    }

    @PostMapping
    public String createSlot(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireManage();

        Map<String, Object> body = new LinkedHashMap<>();
        if (params.get("office_id") != null && !params.get("office_id").isBlank()) {
            body.put("office_id", Long.parseLong(params.get("office_id").trim()));
        }
        if (params.get("counter_id") != null && !params.get("counter_id").isBlank()) {
            body.put("counter_id", Long.parseLong(params.get("counter_id").trim()));
        }
        body.put("slot_date", params.get("slot_date"));
        body.put("start_time", params.get("start_time"));
        body.put("end_time", params.get("end_time"));
        if (params.get("capacity") != null && !params.get("capacity").isBlank()) {
            body.put("capacity", Integer.parseInt(params.get("capacity").trim()));
        }

        try {
            api.post("/appointment-slots", body);
            ra.addFlashAttribute("flash", "Appointment slot created successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to create slot: " + e.getMessage());
        }

        return "redirect:/admin/slots";
    }

    @PostMapping("/{id}/capacity")
    public String updateCapacity(@PathVariable long id, @RequestParam int capacity, RedirectAttributes ra) {
        requireManage();
        try {
            api.patch("/appointment-slots/" + id, Map.of("capacity", capacity));
            ra.addFlashAttribute("flash", "Slot capacity updated.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to update capacity: " + e.getMessage());
        }
        return "redirect:/admin/slots";
    }

    private void requireManage() {
        if (!session.can("appointment.manage")) {
            throw new ApiException(403, "FORBIDDEN", "You do not have permission to manage appointment slots.", null);
        }
    }
}
