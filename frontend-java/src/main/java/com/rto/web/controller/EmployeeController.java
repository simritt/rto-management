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
public class EmployeeController {

    private final ApiClient api;
    private final UserSession session;

    public EmployeeController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    @GetMapping("/employees")
    public String listEmployees(
            @RequestParam(name = "office_id", required = false) Long officeId,
            @RequestParam(name = "designation_id", required = false) Long designationId,
            @RequestParam(name = "is_active", required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (officeId != null) query.put("office_id", officeId);
        if (designationId != null) query.put("designation_id", designationId);
        if (isActive != null) query.put("is_active", isActive);

        JsonNode response = api.get("/employees", query);
        model.addAttribute("page", response);
        model.addAttribute("officeId", officeId);
        model.addAttribute("designationId", designationId);
        model.addAttribute("isActive", isActive);

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("designations", api.get("/reference/designations"));
        } catch (Exception ignored) {}

        return "employees/list";
    }

    @GetMapping("/employees/new")
    public String newEmployeeForm(Model model) {
        requireAuth();

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("designations", api.get("/reference/designations"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("departments", api.get("/reference/departments"));
        } catch (Exception ignored) {}

        return "employees/form";
    }

    @PostMapping("/employees")
    public String createEmployee(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("person_id", Long.parseLong(params.get("person_id").trim()));
            body.put("designation_id", Long.parseLong(params.get("designation_id").trim()));
            body.put("date_joined", params.get("date_joined").trim());

            String empCode = params.get("employee_code");
            if (empCode != null && !empCode.isBlank()) {
                body.put("employee_code", empCode.trim());
            }

            String officeIdStr = params.get("office_id");
            String deptIdStr = params.get("department_id");
            String postedFromStr = params.get("posted_from");

            if (officeIdStr != null && !officeIdStr.isBlank() &&
                deptIdStr != null && !deptIdStr.isBlank() &&
                postedFromStr != null && !postedFromStr.isBlank()) {
                Map<String, Object> posting = new LinkedHashMap<>();
                posting.put("office_id", Long.parseLong(officeIdStr.trim()));
                posting.put("department_id", Long.parseLong(deptIdStr.trim()));
                posting.put("posted_from", postedFromStr.trim());
                body.put("posting", posting);
            }

            JsonNode created = api.post("/employees", body);
            long id = created.path("employee_id").asLong();
            ra.addFlashAttribute("flash", "Employee record created successfully (ID #" + id + ", Code: " + created.path("employee_code").asText() + ")");
            return "redirect:/employees/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/employees/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form input: " + e.getMessage());
            return "redirect:/employees/new";
        }
    }

    @GetMapping("/employees/{id}")
    public String viewEmployee(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode emp = api.get("/employees/" + id);
        model.addAttribute("employee", emp);

        try {
            model.addAttribute("postings", api.get("/employees/" + id + "/postings"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("departments", api.get("/reference/departments"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("designations", api.get("/reference/designations"));
        } catch (Exception ignored) {}

        return "employees/detail";
    }

    @PostMapping("/employees/{id}/postings")
    public String transferPosting(
            @PathVariable Long id,
            @RequestParam("office_id") Long officeId,
            @RequestParam("department_id") Long deptId,
            @RequestParam("posted_from") String postedFrom,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("office_id", officeId);
            body.put("department_id", deptId);
            body.put("posted_from", postedFrom.trim());

            api.post("/employees/" + id + "/postings", body);
            ra.addFlashAttribute("flash", "Employee transfer posting registered successfully");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/employees/" + id;
    }

    @PostMapping("/employees/{id}/status")
    public String updateEmployeeStatus(
            @PathVariable Long id,
            @RequestParam(name = "is_active", defaultValue = "false") boolean isActive,
            @RequestParam(name = "designation_id", required = false) Long designationId,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("is_active", isActive);
            if (designationId != null) {
                body.put("designation_id", designationId);
            }

            api.patch("/employees/" + id, body);
            ra.addFlashAttribute("flash", "Employee status updated (Active: " + isActive + ")");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/employees/" + id;
    }
}
