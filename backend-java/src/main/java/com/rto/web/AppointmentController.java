package com.rto.web;

import com.rto.core.*;
import com.rto.domain.Appointment;
import com.rto.dto.ApplicationDto.*;
import com.rto.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "appointments")
public class AppointmentController {
    private final AppointmentService svc;

    public AppointmentController(AppointmentService svc) {
        this.svc = svc;
    }

    @GetMapping("/appointment-slots")
    public PageResponse<SlotOut> slots(@RequestParam(name = "office_id", required = false) Long officeId,
                                       @RequestParam(name = "counter_id", required = false) Long counterId,
                                       @RequestParam(name = "date_from", required = false) LocalDate from,
                                       @RequestParam(name = "date_to", required = false) LocalDate to,
                                       @RequestParam(name = "available_only", defaultValue = "false") boolean availableOnly, PageParams p) {
        return svc.slots(p, officeId, counterId, from, to, availableOnly);
    }

    @PostMapping("/appointment-slots")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("appointment.manage")
    public SlotOut createSlot(@Valid @RequestBody SlotCreate body, CurrentUser user) {
        return svc.createSlot(body, user);
    }

    @PatchMapping("/appointment-slots/{slotId}")
    @Requires("appointment.manage")
    @Operation(summary = "Change capacity (never below the seats already booked)")
    public SlotOut updateSlot(@PathVariable Long slotId, @Valid @RequestBody SlotUpdate body, CurrentUser user) {
        return svc.updateSlot(slotId, body.capacity(), user);
    }

    @PostMapping("/applications/{applicationId}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("appointment.book")
    @Operation(summary = "Book a slot. 409 SLOT_FULL when capacity is exhausted; capacity can never be exceeded")
    public Appointment book(@PathVariable Long applicationId, @Valid @RequestBody BookAppointment body, CurrentUser user) {
        return svc.book(applicationId, body.slotId(), user);
    }

    @GetMapping("/appointments")
    public PageResponse<Appointment> list(@RequestParam(name = "office_id", required = false) Long officeId,
                                          @RequestParam(name = "slot_date", required = false) LocalDate slotDate,
                                          @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                          @RequestParam(name = "application_id", required = false) Long applicationId,
                                          PageParams p, CurrentUser user) {
        return svc.list(user, p, officeId, slotDate, status, applicationId);
    }

    @PatchMapping("/appointments/{appointmentId}/cancel")
    @Requires("appointment.book")
    @Operation(summary = "Cancel and release the seat")
    public Appointment cancel(@PathVariable Long appointmentId, CurrentUser user) {
        return svc.cancel(appointmentId, user);
    }

    @PatchMapping("/appointments/{appointmentId}/reschedule")
    @Requires("appointment.book")
    @Operation(summary = "Move to another slot; both slots' capacities are preserved atomically")
    public Appointment reschedule(@PathVariable Long appointmentId, @Valid @RequestBody BookAppointment body, CurrentUser user) {
        return svc.reschedule(appointmentId, body.slotId(), user);
    }

    @PatchMapping("/appointments/{appointmentId}/outcome")
    @Requires("appointment.manage")
    @Operation(summary = "Mark NO_SHOW or COMPLETED")
    public Appointment outcome(@PathVariable Long appointmentId, @Valid @RequestBody AppointmentStatusChange body, CurrentUser user) {
        return svc.outcome(appointmentId, body.status(), user);
    }
}
