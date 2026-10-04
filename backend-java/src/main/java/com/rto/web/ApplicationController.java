package com.rto.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.Appointment;
import com.rto.domain.ApplicationStatusHistory;
import com.rto.dto.ApplicationDto.*;
import com.rto.service.AppointmentService;
import com.rto.service.ApplicationService;
import com.rto.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "applications")
public class ApplicationController {
    private final ApplicationService apps;
    private final DocumentService docs;

    public ApplicationController(ApplicationService apps, DocumentService docs) {
        this.apps = apps;
        this.docs = docs;
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAny({"application.create", "application.create_own"})
    @Operation(summary = "Submit an application (status SUBMITTED, history + audit written)")
    public ApplicationDetail create(@Valid @RequestBody ApplicationCreate body, CurrentUser user) {
        return apps.create(body, user);
    }

    @GetMapping("/applications")
    @Operation(summary = "Search applications (staff: all; citizens: only their own)")
    public PageResponse<ApplicationOut> list(
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
            @RequestParam(name = "service_type_id", required = false) Long serviceTypeId,
            @RequestParam(name = "office_id", required = false) Long officeId,
            @RequestParam(name = "citizen_id", required = false) Long citizenId,
            @RequestParam(name = "assigned_officer_id", required = false) Long assignedOfficerId,
            @RequestParam(name = "submitted_from", required = false) LocalDate from,
            @RequestParam(name = "submitted_to", required = false) LocalDate to, PageParams p, CurrentUser user) {
        return apps.list(user, p, status, serviceTypeId, officeId, citizenId, assignedOfficerId, from, to);
    }

    @GetMapping("/applications/{applicationId}")
    public ApplicationDetail get(@PathVariable Long applicationId, CurrentUser user) {
        return apps.view(user, applicationId);
    }

    @PatchMapping("/applications/{applicationId}")
    @Requires("application.update")
    @Operation(summary = "Edit remarks / office of an open application (status changes use /status)")
    public ApplicationDetail update(@PathVariable Long applicationId, @RequestBody JsonNode body, CurrentUser user) {
        return apps.update(applicationId, body, user);
    }

    @PostMapping("/applications/{applicationId}/assign")
    @Requires("application.assign")
    @Operation(summary = "Assign an officer who is currently posted at the application's office")
    public ApplicationDetail assign(@PathVariable Long applicationId, @Valid @RequestBody AssignRequest body, CurrentUser user) {
        return apps.assign(applicationId, body.officerId(), user);
    }

    @PostMapping("/applications/{applicationId}/status")
    @Operation(summary = "Move through the workflow; invalid transitions return 409")
    public ApplicationDetail status(@PathVariable Long applicationId, @Valid @RequestBody StatusChange body, CurrentUser user) {
        return apps.changeStatus(applicationId, body.status(), body.reason(), user);
    }

    @GetMapping("/applications/{applicationId}/history")
    public List<ApplicationStatusHistory> history(@PathVariable Long applicationId, CurrentUser user) {
        return apps.history(user, applicationId);
    }

    // ---- documents ------------------------------------------------------------------------------------------

    @PostMapping(value = "/applications/{applicationId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("document.upload")
    @Operation(summary = "Upload a document (multipart). Only metadata + file_path are stored in MySQL")
    public DocumentOut upload(@PathVariable Long applicationId, @RequestParam("document_type_id") Long documentTypeId,
                              @RequestParam("file") MultipartFile file, CurrentUser user) {
        return docs.upload(applicationId, documentTypeId, file, user);
    }

    @GetMapping("/applications/{applicationId}/documents")
    public List<DocumentOut> documents(@PathVariable Long applicationId, CurrentUser user) {
        return docs.list(applicationId, user);
    }

    @GetMapping("/documents/{documentId}/file")
    @Operation(summary = "Download the stored file")
    public ResponseEntity<byte[]> download(@PathVariable Long documentId, CurrentUser user) {
        DocumentService.FileContent f = docs.file(documentId, user);
        String ext = f.path().substring(f.path().lastIndexOf('.') + 1);
        MediaType type = switch (ext) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "png" -> MediaType.IMAGE_PNG;
            default -> MediaType.IMAGE_JPEG;
        };
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document-" + documentId + "." + ext + "\"")
                .body(f.bytes());
    }

    @PatchMapping("/documents/{documentId}/verify")
    @Requires("document.verify")
    public DocumentOut verify(@PathVariable Long documentId, CurrentUser user) {
        return docs.verify(documentId, user);
    }

    @PatchMapping("/documents/{documentId}/reject")
    @Requires("document.verify")
    public DocumentOut reject(@PathVariable Long documentId, @Valid @RequestBody RejectDocument body, CurrentUser user) {
        return docs.reject(documentId, body.reason(), user);
    }

    @PatchMapping("/documents/{documentId}/expire")
    @Requires("document.verify")
    @Operation(summary = "Persist EXPIRED for a verified document")
    public DocumentOut expire(@PathVariable Long documentId, CurrentUser user) {
        return docs.expire(documentId, user);
    }
}
