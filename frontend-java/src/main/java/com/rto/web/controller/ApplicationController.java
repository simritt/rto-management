package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import com.rto.web.util.Forms;
import com.rto.web.util.Paging;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/applications")
public class ApplicationController {

    private final ApiClient api;
    private final UserSession session;

    public ApplicationController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long office_id,
            @RequestParam(required = false) Long service_type_id,
            Model model) {
        requireAuth();

        Map<String, Object> filters = new LinkedHashMap<>();
        if (status != null && !status.isBlank()) filters.put("status", status.trim());
        if (office_id != null) filters.put("office_id", office_id);
        if (service_type_id != null) filters.put("service_type_id", service_type_id);

        Map<String, Object> query = new LinkedHashMap<>(filters);
        query.put("page", page);
        query.put("page_size", 15);

        JsonNode result = api.get("/applications", query);
        JsonNode offices = api.get("/offices", Map.of("page", 1, "page_size", 100));
        JsonNode serviceTypes = api.get("/service-types", Map.of("page", 1, "page_size", 100));

        model.addAttribute("result", result);
        model.addAttribute("offices", offices.path("items"));
        model.addAttribute("serviceTypes", serviceTypes.path("items"));
        model.addAttribute("status", status);
        model.addAttribute("office_id", office_id);
        model.addAttribute("service_type_id", service_type_id);
        model.addAttribute("pagerBase", Paging.base("/applications", filters));

        return "applications/list";
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) Long citizen_id,
            Model model) {
        requireAuth();

        JsonNode offices = api.get("/offices", Map.of("page", 1, "page_size", 100, "is_active", true));
        JsonNode serviceTypes = api.get("/service-types", Map.of("page", 1, "page_size", 100));
        JsonNode citizens = api.get("/citizens", Map.of("page", 1, "page_size", 50, "blacklisted", false));

        Map<String, String> form = new LinkedHashMap<>();
        if (citizen_id != null) {
            form.put("citizen_id", citizen_id.toString());
        }

        model.addAttribute("offices", offices.path("items"));
        model.addAttribute("serviceTypes", serviceTypes.path("items"));
        model.addAttribute("citizens", citizens.path("items"));
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", Map.of());

        return "applications/form";
    }

    @PostMapping
    public String create(@RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        requireAuth();

        Map<String, Object> body = new LinkedHashMap<>();
        if (params.get("citizen_id") != null && !params.get("citizen_id").isBlank()) {
            body.put("citizen_id", Long.parseLong(params.get("citizen_id").trim()));
        }
        if (params.get("service_type_id") != null && !params.get("service_type_id").isBlank()) {
            body.put("service_type_id", Long.parseLong(params.get("service_type_id").trim()));
        }
        if (params.get("office_id") != null && !params.get("office_id").isBlank()) {
            body.put("office_id", Long.parseLong(params.get("office_id").trim()));
        }
        if (params.get("remarks") != null && !params.get("remarks").isBlank()) {
            body.put("remarks", params.get("remarks").trim());
        }
        if (params.get("application_number") != null && !params.get("application_number").isBlank()) {
            body.put("application_number", params.get("application_number").trim().toUpperCase());
        }

        try {
            JsonNode created = api.post("/applications", body);
            ra.addFlashAttribute("flash", "Application " + created.path("application_number").asText() + " submitted successfully.");
            return "redirect:/applications/" + created.path("application_id").asLong();
        } catch (ApiException e) {
            JsonNode offices = api.get("/offices", Map.of("page", 1, "page_size", 100, "is_active", true));
            JsonNode serviceTypes = api.get("/service-types", Map.of("page", 1, "page_size", 100));
            JsonNode citizens = api.get("/citizens", Map.of("page", 1, "page_size", 50, "blacklisted", false));

            model.addAttribute("offices", offices.path("items"));
            model.addAttribute("serviceTypes", serviceTypes.path("items"));
            model.addAttribute("citizens", citizens.path("items"));
            model.addAttribute("form", params);
            model.addAttribute("fieldErrors", Forms.fieldErrors(e.errors()));
            model.addAttribute("formError", e.getMessage());
            return "applications/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        requireAuth();

        JsonNode app = api.get("/applications/" + id);
        JsonNode docs = api.get("/applications/" + id + "/documents");
        JsonNode history = api.get("/applications/" + id + "/history");
        JsonNode docTypes = api.get("/document-types", Map.of("page", 1, "page_size", 100));

        // Available slots for the application's office
        long officeId = app.path("office_id").asLong();
        JsonNode slots = api.get("/appointment-slots", Map.of("office_id", officeId, "available_only", true, "page_size", 50));

        model.addAttribute("app", app);
        model.addAttribute("docs", docs);
        model.addAttribute("history", history);
        model.addAttribute("docTypes", docTypes.path("items"));
        model.addAttribute("slots", slots.path("items"));

        return "applications/detail";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable long id,
            @RequestParam String status,
            @RequestParam(required = false) String reason,
            RedirectAttributes ra) {
        requireAuth();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", status.trim());
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason.trim());
        }

        try {
            api.post("/applications/" + id + "/status", body);
            ra.addFlashAttribute("flash", "Application status updated to " + status + ".");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to update status: " + e.getMessage());
        }

        return "redirect:/applications/" + id;
    }

    @PostMapping("/{id}/documents")
    public String uploadDocument(
            @PathVariable long id,
            @RequestParam("document_type_id") Long documentTypeId,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes ra) {
        requireAuth();

        if (file.isEmpty()) {
            ra.addFlashAttribute("flashError", "Please select a file to upload.");
            return "redirect:/applications/" + id;
        }

        try {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("document_type_id", documentTypeId);
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
                }
            };
            parts.add("file", fileResource);

            api.uploadMultipart("/applications/" + id + "/documents", parts);
            ra.addFlashAttribute("flash", "Document uploaded successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Document upload failed: " + e.getMessage());
        } catch (IOException e) {
            ra.addFlashAttribute("flashError", "Could not read file: " + e.getMessage());
        }

        return "redirect:/applications/" + id;
    }

    @PostMapping("/{appId}/documents/{docId}/verify")
    public String verifyDocument(
            @PathVariable long appId,
            @PathVariable long docId,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.patch("/documents/" + docId + "/verify", Map.of());
            ra.addFlashAttribute("flash", "Document verified.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Verification failed: " + e.getMessage());
        }

        return "redirect:/applications/" + appId;
    }

    @PostMapping("/{appId}/documents/{docId}/reject")
    public String rejectDocument(
            @PathVariable long appId,
            @PathVariable long docId,
            @RequestParam String reason,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.patch("/documents/" + docId + "/reject", Map.of("reason", reason));
            ra.addFlashAttribute("flash", "Document rejected.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Rejection failed: " + e.getMessage());
        }

        return "redirect:/applications/" + appId;
    }

    @GetMapping("/documents/{docId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable long docId) {
        requireAuth();
        return api.getFile("/documents/" + docId + "/file");
    }

    @PostMapping("/{id}/appointments")
    public String bookAppointment(
            @PathVariable long id,
            @RequestParam("slot_id") Long slotId,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.post("/applications/" + id + "/appointments", Map.of("slot_id", slotId));
            ra.addFlashAttribute("flash", "Appointment slot booked successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Appointment booking failed: " + e.getMessage());
        }

        return "redirect:/applications/" + id;
    }

    @PostMapping("/{appId}/appointments/{aptId}/cancel")
    public String cancelAppointment(
            @PathVariable long appId,
            @PathVariable long aptId,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.patch("/appointments/" + aptId + "/cancel", Map.of());
            ra.addFlashAttribute("flash", "Appointment cancelled and seat released.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Cancellation failed: " + e.getMessage());
        }

        return "redirect:/applications/" + appId;
    }

    @PostMapping("/{appId}/appointments/{aptId}/outcome")
    public String recordOutcome(
            @PathVariable long appId,
            @PathVariable long aptId,
            @RequestParam String outcome_status,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.patch("/appointments/" + aptId + "/outcome", Map.of("status", outcome_status));
            ra.addFlashAttribute("flash", "Appointment recorded as " + outcome_status + ".");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to update appointment outcome: " + e.getMessage());
        }

        return "redirect:/applications/" + appId;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new ApiException(401, "UNAUTHORIZED", "Please sign in to continue.", null);
        }
    }
}
