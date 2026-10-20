package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import com.rto.web.util.Forms;
import com.rto.web.util.Paging;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/offices")
public class AdminOfficeController {

    private final ApiClient api;
    private final UserSession session;

    public AdminOfficeController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Long region_id,
            @RequestParam(required = false) Boolean is_active,
            Model model) {
        requireManage();

        Map<String, Object> filters = new LinkedHashMap<>();
        if (region_id != null) filters.put("region_id", region_id);
        if (is_active != null) filters.put("is_active", is_active);

        Map<String, Object> query = new LinkedHashMap<>(filters);
        query.put("page", page);
        query.put("page_size", 15);

        JsonNode result = api.get("/offices", query);
        JsonNode regions = api.get("/regions", Map.of("page", 1, "page_size", 100));

        model.addAttribute("result", result);
        model.addAttribute("regions", regions.path("items"));
        model.addAttribute("region_id", region_id);
        model.addAttribute("is_active", is_active);
        model.addAttribute("pagerBase", Paging.base("/admin/offices", filters));

        return "admin/offices/list";
    }

    @GetMapping("/new")
    public String newOfficeForm(Model model) {
        requireManage();
        JsonNode regions = api.get("/regions", Map.of("page", 1, "page_size", 100));
        model.addAttribute("regions", regions.path("items"));
        model.addAttribute("form", Map.of("is_active", "true"));
        model.addAttribute("fieldErrors", Map.of());
        model.addAttribute("action", "/admin/offices");
        model.addAttribute("editing", false);
        return "admin/offices/form";
    }

    @PostMapping
    public String createOffice(@RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        requireManage();
        Map<String, Object> body = new LinkedHashMap<>();
        if (params.get("region_id") != null && !params.get("region_id").isBlank()) {
            body.put("region_id", Long.parseLong(params.get("region_id").trim()));
        }
        body.put("office_name", params.get("office_name") != null ? params.get("office_name").trim() : "");
        body.put("office_code", params.get("office_code") != null ? params.get("office_code").trim().toUpperCase() : "");
        body.put("address_line", params.get("address_line") != null ? params.get("address_line").trim() : "");
        body.put("is_active", "true".equals(params.get("is_active")) || "on".equals(params.get("is_active")));

        try {
            JsonNode created = api.post("/offices", body);
            ra.addFlashAttribute("flash", "Office " + created.path("office_name").asText() + " (" + created.path("office_code").asText() + ") created successfully.");
            return "redirect:/admin/offices/" + created.path("office_id").asLong();
        } catch (ApiException e) {
            JsonNode regions = api.get("/regions", Map.of("page", 1, "page_size", 100));
            model.addAttribute("regions", regions.path("items"));
            model.addAttribute("form", params);
            model.addAttribute("fieldErrors", Forms.fieldErrors(e.errors()));
            model.addAttribute("formError", e.getMessage());
            model.addAttribute("action", "/admin/offices");
            model.addAttribute("editing", false);
            return "admin/offices/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        requireManage();
        JsonNode office = api.get("/offices/" + id);
        JsonNode counters = api.get("/offices/" + id + "/counters");
        model.addAttribute("o", office);
        model.addAttribute("counters", counters);
        return "admin/offices/detail";
    }

    @GetMapping("/{id}/edit")
    public String editOfficeForm(@PathVariable long id, Model model) {
        requireManage();
        JsonNode office = api.get("/offices/" + id);
        JsonNode regions = api.get("/regions", Map.of("page", 1, "page_size", 100));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("region_id", office.path("region_id").asText(""));
        form.put("office_name", office.path("office_name").asText(""));
        form.put("office_code", office.path("office_code").asText(""));
        form.put("address_line", office.path("address_line").asText(""));
        form.put("is_active", office.path("is_active").asBoolean() ? "true" : "false");

        model.addAttribute("o", office);
        model.addAttribute("regions", regions.path("items"));
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", Map.of());
        model.addAttribute("action", "/admin/offices/" + id + "/edit");
        model.addAttribute("editing", true);
        return "admin/offices/form";
    }

    @PostMapping("/{id}/edit")
    public String updateOffice(@PathVariable long id, @RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        requireManage();
        Map<String, Object> body = new LinkedHashMap<>();
        if (params.get("region_id") != null && !params.get("region_id").isBlank()) {
            body.put("region_id", Long.parseLong(params.get("region_id").trim()));
        }
        body.put("office_name", params.get("office_name") != null ? params.get("office_name").trim() : "");
        body.put("address_line", params.get("address_line") != null ? params.get("address_line").trim() : "");
        body.put("is_active", "true".equals(params.get("is_active")) || "on".equals(params.get("is_active")));

        try {
            api.patch("/offices/" + id, body);
            ra.addFlashAttribute("flash", "Office updated successfully.");
            return "redirect:/admin/offices/" + id;
        } catch (ApiException e) {
            JsonNode office = api.get("/offices/" + id);
            JsonNode regions = api.get("/regions", Map.of("page", 1, "page_size", 100));
            model.addAttribute("o", office);
            model.addAttribute("regions", regions.path("items"));
            model.addAttribute("form", params);
            model.addAttribute("fieldErrors", Forms.fieldErrors(e.errors()));
            model.addAttribute("formError", e.getMessage());
            model.addAttribute("action", "/admin/offices/" + id + "/edit");
            model.addAttribute("editing", true);
            return "admin/offices/form";
        }
    }

    @PostMapping("/{id}/counters")
    public String addCounter(@PathVariable long id, @RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireManage();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counter_number", params.get("counter_number") != null ? params.get("counter_number").trim() : "");
        body.put("service_category", params.get("service_category") != null ? params.get("service_category").trim() : "");

        try {
            api.post("/offices/" + id + "/counters", body);
            ra.addFlashAttribute("flash", "Counter added.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to add counter: " + e.getMessage());
        }
        return "redirect:/admin/offices/" + id;
    }

    @PostMapping("/{officeId}/counters/{counterId}/delete")
    public String deleteCounter(@PathVariable long officeId, @PathVariable long counterId, RedirectAttributes ra) {
        requireManage();
        try {
            api.delete("/counters/" + counterId);
            ra.addFlashAttribute("flash", "Counter deleted.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to delete counter: " + e.getMessage());
        }
        return "redirect:/admin/offices/" + officeId;
    }

    private void requireManage() {
        if (!session.can("office.manage")) {
            throw new ApiException(403, "FORBIDDEN", "You do not have permission to manage offices (requires office.manage).", null);
        }
    }
}
