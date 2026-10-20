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
public class SupportController {

    private final ApiClient api;
    private final UserSession session;

    public SupportController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    // =========================================================================
    // 1. COMPLAINTS (/complaints)
    // =========================================================================

    @GetMapping("/complaints")
    public String listComplaints(
            @RequestParam(required = false) String status,
            @RequestParam(name = "office_id", required = false) Long officeId,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (officeId != null) query.put("office_id", officeId);
        if (citizenId != null) query.put("citizen_id", citizenId);
        if (search != null && !search.isBlank()) query.put("search", search.trim());

        JsonNode response = api.get("/complaints", query);
        model.addAttribute("page", response);
        model.addAttribute("status", status);
        model.addAttribute("officeId", officeId);
        model.addAttribute("citizenId", citizenId);
        model.addAttribute("search", search);

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}

        return "complaints/list";
    }

    @GetMapping("/complaints/new")
    public String newComplaintForm(
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(name = "office_id", required = false) Long officeId,
            Model model) {
        requireAuth();

        model.addAttribute("preCitizenId", citizenId);
        model.addAttribute("preOfficeId", officeId);

        try {
            model.addAttribute("offices", api.get("/offices").path("items"));
        } catch (Exception ignored) {}

        return "complaints/form";
    }

    @PostMapping("/complaints")
    public String createComplaint(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("office_id", Long.parseLong(params.get("office_id")));
            body.put("subject", params.get("subject").trim());
            body.put("description", params.get("description").trim());

            String citizenIdStr = params.get("citizen_id");
            if (citizenIdStr != null && !citizenIdStr.isBlank()) {
                body.put("citizen_id", Long.parseLong(citizenIdStr.trim()));
            }

            JsonNode created = api.post("/complaints", body);
            long id = created.path("complaint_id").asLong();
            ra.addFlashAttribute("flash", "Complaint filed successfully (Ticket #" + id + ")");
            return "redirect:/complaints/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/complaints/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form data: " + e.getMessage());
            return "redirect:/complaints/new";
        }
    }

    @GetMapping("/complaints/{id}")
    public String viewComplaint(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode complaint = api.get("/complaints/" + id);
        model.addAttribute("complaint", complaint);

        try {
            long citizenId = complaint.path("citizen_id").asLong();
            if (citizenId > 0) {
                model.addAttribute("citizen", api.get("/citizens/" + citizenId));
            }
        } catch (Exception ignored) {}

        try {
            long officeId = complaint.path("office_id").asLong();
            if (officeId > 0) {
                model.addAttribute("office", api.get("/offices/" + officeId));
            }
        } catch (Exception ignored) {}

        return "complaints/detail";
    }

    @PostMapping("/complaints/{id}/status")
    public String updateComplaintStatus(
            @PathVariable Long id,
            @RequestParam("status") String nextStatus,
            @RequestParam(name = "note", required = false) String note,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", nextStatus.trim());
            if (note != null && !note.isBlank()) {
                body.put("note", note.trim());
            }

            api.patch("/complaints/" + id + "/status", body);
            ra.addFlashAttribute("flash", "Complaint status updated to " + nextStatus);
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/complaints/" + id;
    }

    // =========================================================================
    // 2. APPEALS (/appeals)
    // =========================================================================

    @GetMapping("/appeals")
    public String listAppeals(
            @RequestParam(required = false) String status,
            @RequestParam(name = "against_type", required = false) String againstType,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (againstType != null && !againstType.isBlank()) query.put("against_type", againstType.trim());
        if (citizenId != null) query.put("citizen_id", citizenId);
        if (search != null && !search.isBlank()) query.put("search", search.trim());

        JsonNode response = api.get("/appeals", query);
        model.addAttribute("page", response);
        model.addAttribute("status", status);
        model.addAttribute("againstType", againstType);
        model.addAttribute("citizenId", citizenId);
        model.addAttribute("search", search);

        return "appeals/list";
    }

    @GetMapping("/appeals/new")
    public String newAppealForm(
            @RequestParam(name = "against_type", required = false) String againstType,
            @RequestParam(name = "against_id", required = false) Long againstId,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            Model model) {
        requireAuth();

        model.addAttribute("preAgainstType", againstType);
        model.addAttribute("preAgainstId", againstId);
        model.addAttribute("preCitizenId", citizenId);

        return "appeals/form";
    }

    @PostMapping("/appeals")
    public String createAppeal(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("against_type", params.get("against_type").trim());
            body.put("against_id", Long.parseLong(params.get("against_id").trim()));
            body.put("grounds", params.get("grounds").trim());

            String citizenIdStr = params.get("citizen_id");
            if (citizenIdStr != null && !citizenIdStr.isBlank()) {
                body.put("citizen_id", Long.parseLong(citizenIdStr.trim()));
            }

            JsonNode created = api.post("/appeals", body);
            long id = created.path("appeal_id").asLong();
            ra.addFlashAttribute("flash", "Appeal filed successfully (#" + id + ")");
            return "redirect:/appeals/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/appeals/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form data: " + e.getMessage());
            return "redirect:/appeals/new";
        }
    }

    @GetMapping("/appeals/{id}")
    public String viewAppeal(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode appeal = api.get("/appeals/" + id);
        model.addAttribute("appeal", appeal);

        try {
            long citizenId = appeal.path("citizen_id").asLong();
            if (citizenId > 0) {
                model.addAttribute("citizen", api.get("/citizens/" + citizenId));
            }
        } catch (Exception ignored) {}

        return "appeals/detail";
    }

    @PostMapping("/appeals/{id}/review")
    public String startAppealReview(@PathVariable Long id, RedirectAttributes ra) {
        requireAuth();

        try {
            api.post("/appeals/" + id + "/review", Map.of());
            ra.addFlashAttribute("flash", "Appeal is now UNDER_REVIEW");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/appeals/" + id;
    }

    @PostMapping("/appeals/{id}/uphold")
    public String upholdAppeal(
            @PathVariable Long id,
            @RequestParam(name = "note", required = false) String note,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (note != null && !note.isBlank()) {
                body.put("note", note.trim());
            }
            api.post("/appeals/" + id + "/uphold", body);
            ra.addFlashAttribute("flash", "Appeal UPHELD: remedy applied to target item");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/appeals/" + id;
    }

    @PostMapping("/appeals/{id}/dismiss")
    public String dismissAppeal(
            @PathVariable Long id,
            @RequestParam(name = "note", required = false) String note,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (note != null && !note.isBlank()) {
                body.put("note", note.trim());
            }
            api.post("/appeals/" + id + "/dismiss", body);
            ra.addFlashAttribute("flash", "Appeal DISMISSED");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/appeals/" + id;
    }

    // =========================================================================
    // 3. NOTIFICATIONS (/notifications)
    // =========================================================================

    @GetMapping("/notifications")
    public String listNotifications(
            @RequestParam(required = false) String status,
            @RequestParam(name = "person_id", required = false) Long personId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (personId != null) query.put("person_id", personId);

        JsonNode response = api.get("/notifications", query);
        model.addAttribute("page", response);
        model.addAttribute("status", status);
        model.addAttribute("personId", personId);

        return "notifications/list";
    }

    @PostMapping("/notifications/expiry-reminders")
    public String sendExpiryReminders(
            @RequestParam(name = "days", defaultValue = "30") int days,
            RedirectAttributes ra) {
        requireAuth();

        try {
            JsonNode result = api.post("/notifications/expiry-reminders?days=" + days, Map.of());
            long queued = result.path("reminders_queued").asLong();
            ra.addFlashAttribute("flash", "Licence expiry sweep complete: " + queued + " notification(s) dispatched for licences expiring within " + days + " days.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/notifications";
    }

    // =========================================================================
    // 4. AUDIT LOGS (/audit-logs)
    // =========================================================================

    @GetMapping("/audit-logs")
    public String listAuditLogs(
            @RequestParam(name = "table_name", required = false) String tableName,
            @RequestParam(name = "record_id", required = false) Long recordId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "25") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (tableName != null && !tableName.isBlank()) query.put("table_name", tableName.trim());
        if (recordId != null) query.put("record_id", recordId);
        if (action != null && !action.isBlank()) query.put("action", action.trim());
        if (userId != null) query.put("user_id", userId);
        if (dateFrom != null && !dateFrom.isBlank()) query.put("date_from", dateFrom.trim());
        if (dateTo != null && !dateTo.isBlank()) query.put("date_to", dateTo.trim());

        JsonNode response = api.get("/audit-logs", query);
        model.addAttribute("page", response);
        model.addAttribute("tableName", tableName);
        model.addAttribute("recordId", recordId);
        model.addAttribute("action", action);
        model.addAttribute("userId", userId);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);

        return "audit/list";
    }
}
