package com.rto.web;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.LicenceDto.ReminderResult;
import com.rto.dto.SupportDto.*;
import com.rto.service.DashboardService;
import com.rto.service.LicenceService;
import com.rto.service.Notifications;
import com.rto.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "complaints, appeals, notifications, audit & dashboard")
public class SupportController {
    private final SupportService svc;
    private final Notifications notifications;
    private final LicenceService licences;
    private final DashboardService dashboard;

    public SupportController(SupportService svc, Notifications notifications, LicenceService licences, DashboardService dashboard) {
        this.svc = svc;
        this.notifications = notifications;
        this.licences = licences;
        this.dashboard = dashboard;
    }

    // ---- complaints -------------------------------------------------------------------------------------------

    @PostMapping("/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("complaint.create")
    public Complaint createComplaint(@Valid @RequestBody ComplaintCreate body, CurrentUser user) {
        return svc.createComplaint(body, user);
    }

    @GetMapping("/complaints")
    @Operation(summary = "Staff: all complaints; citizens: their own")
    public PageResponse<Complaint> complaints(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                              @RequestParam(name = "office_id", required = false) Long officeId,
                                              @RequestParam(name = "citizen_id", required = false) Long citizenId, PageParams p, CurrentUser user) {
        return svc.complaints(user, p, status, officeId, citizenId);
    }

    @GetMapping("/complaints/{complaintId}")
    public Complaint complaint(@PathVariable Long complaintId, CurrentUser user) {
        return svc.complaint(user, complaintId);
    }

    @PatchMapping("/complaints/{complaintId}/status")
    @Requires("complaint.manage")
    @Operation(summary = "Move a complaint forward (no arbitrary backwards moves)")
    public Complaint complaintStatus(@PathVariable Long complaintId, @Valid @RequestBody ComplaintStatusChange body, CurrentUser user) {
        return svc.changeComplaintStatus(complaintId, body.status(), body.note(), user);
    }

    // ---- appeals ----------------------------------------------------------------------------------------------

    @PostMapping("/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("appeal.create")
    @Operation(summary = "File an appeal; the polymorphic target is verified to exist and be appealable")
    public Appeal createAppeal(@Valid @RequestBody AppealCreate body, CurrentUser user) {
        return svc.createAppeal(body, user);
    }

    @GetMapping("/appeals")
    public PageResponse<Appeal> appeals(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                        @RequestParam(name = "against_type", required = false) @Pattern(regexp = "^(CHALLAN|APPLICATION_REJECTION|LICENCE_SUSPENSION)$") String againstType,
                                        @RequestParam(name = "citizen_id", required = false) Long citizenId, PageParams p, CurrentUser user) {
        return svc.appeals(user, p, status, againstType, citizenId);
    }

    @GetMapping("/appeals/{appealId}")
    public Appeal appeal(@PathVariable Long appealId, CurrentUser user) {
        return svc.appeal(user, appealId);
    }

    @PostMapping("/appeals/{appealId}/review")
    @Requires("appeal.review")
    @Operation(summary = "FILED -> UNDER_REVIEW")
    public Appeal review(@PathVariable Long appealId, CurrentUser user) {
        return svc.startReview(appealId, user);
    }

    @PostMapping("/appeals/{appealId}/uphold")
    @Requires("appeal.review")
    @Operation(summary = "UNDER_REVIEW -> UPHELD and apply the remedy (cancel challan / reopen application / reinstate licence)")
    public Appeal uphold(@PathVariable Long appealId, @RequestBody(required = false) AppealDecision body, CurrentUser user) {
        return svc.decide(appealId, "UPHELD", body == null ? null : body.note(), user);
    }

    @PostMapping("/appeals/{appealId}/dismiss")
    @Requires("appeal.review")
    @Operation(summary = "UNDER_REVIEW -> DISMISSED")
    public Appeal dismiss(@PathVariable Long appealId, @RequestBody(required = false) AppealDecision body, CurrentUser user) {
        return svc.decide(appealId, "DISMISSED", body == null ? null : body.note(), user);
    }

    // ---- notifications ----------------------------------------------------------------------------------------

    @GetMapping("/notifications")
    @Operation(summary = "Staff (notification.view): all; others: their own")
    public PageResponse<Notification> notifications(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                                    @RequestParam(name = "person_id", required = false) Long personId, PageParams p, CurrentUser user) {
        return notifications.list(user, p, status, personId);
    }

    @PostMapping("/notifications/expiry-reminders")
    @RequiresAny({"licence.issue", "notification.view"})
    @Operation(summary = "Queue 'licence nearing expiry' notifications for licences expiring within `days`")
    public ReminderResult expiryReminders(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return licences.sendExpiryReminders(days);
    }

    // ---- audit ------------------------------------------------------------------------------------------------

    @GetMapping("/audit-logs")
    @Requires("audit.view")
    @Operation(summary = "Append-only audit trail (read-only; there is deliberately no write/delete API)")
    public PageResponse<AuditLog> audit(@RequestParam(name = "table_name", required = false) @Size(max = 64) String table,
                                        @RequestParam(name = "record_id", required = false) Long recordId,
                                        @RequestParam(required = false) @Pattern(regexp = "^(INSERT|UPDATE|DELETE)$") String action,
                                        @RequestParam(name = "user_id", required = false) Long userId,
                                        @RequestParam(name = "date_from", required = false) LocalDate from,
                                        @RequestParam(name = "date_to", required = false) LocalDate to, PageParams p) {
        return svc.audit(p, table, recordId, action, userId, from, to);
    }

    // ---- dashboard --------------------------------------------------------------------------------------------

    @GetMapping("/dashboard/summary")
    @Requires("dashboard.view")
    @Operation(summary = "Live aggregates straight from MySQL")
    public DashboardSummary summary(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return dashboard.summary(days);
    }
}
