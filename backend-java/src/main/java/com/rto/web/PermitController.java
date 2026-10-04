package com.rto.web;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.PermitDto.*;
import com.rto.service.PermitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "routes & permits")
public class PermitController {
    private final PermitService svc;

    public PermitController(PermitService svc) {
        this.svc = svc;
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("route.manage")
    public Route createRoute(@Valid @RequestBody RouteCreate body, CurrentUser user) {
        return svc.createRoute(body, user);
    }

    @GetMapping("/routes")
    @Requires("permit.view")
    public PageResponse<Route> routes(PageParams p) {
        return svc.routes(p);
    }

    @GetMapping("/routes/{routeId}")
    @Requires("permit.view")
    @Operation(summary = "Route with its ordered segments")
    public RouteDetail route(@PathVariable Long routeId) {
        return svc.route(routeId);
    }

    @PostMapping("/routes/{routeId}/segments")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("route.manage")
    @Operation(summary = "Add a segment; sequence_no is unique per route and auto-numbered when omitted")
    public RouteSegment addSegment(@PathVariable Long routeId, @Valid @RequestBody SegmentCreate body, CurrentUser user) {
        return svc.addSegment(routeId, body, user);
    }

    @GetMapping("/routes/{routeId}/segments")
    @Requires("permit.view")
    @Operation(summary = "Segments in sequence order")
    public List<RouteSegment> segments(@PathVariable Long routeId) {
        return svc.segments(routeId);
    }

    @PostMapping("/permits")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("permit.create")
    @Operation(summary = "Issue a permit to a commercial vehicle (application must be APPROVED)")
    public Permit create(@Valid @RequestBody PermitCreate body, CurrentUser user) {
        return svc.create(body, user);
    }

    @GetMapping("/permits")
    @Requires("permit.view")
    public PageResponse<Permit> list(@RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                     @RequestParam(name = "citizen_id", required = false) Long citizenId,
                                     @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                     @RequestParam(name = "permit_type_id", required = false) Long permitTypeId,
                                     @RequestParam(name = "route_id", required = false) Long routeId,
                                     @RequestParam(name = "expiring_within_days", required = false) @Min(0) @Max(3650) Integer expiring, PageParams p) {
        return svc.list(p, vehicleId, citizenId, status, permitTypeId, routeId, expiring);
    }

    @PostMapping("/permits/expire-overdue")
    @Requires("permit.manage")
    @Operation(summary = "Mark lapsed permits EXPIRED (with history)")
    public Map<String, Object> expireOverdue(CurrentUser user) {
        return svc.expireOverdue(user);
    }

    @GetMapping("/permits/{permitId}")
    @Requires("permit.view")
    public Permit get(@PathVariable Long permitId) {
        return svc.get(permitId);
    }

    @GetMapping("/permits/{permitId}/history")
    @Requires("permit.view")
    public List<PermitStatusHistory> history(@PathVariable Long permitId) {
        return svc.history(permitId);
    }

    @PostMapping("/permits/{permitId}/suspend")
    @Requires("permit.manage")
    public Permit suspend(@PathVariable Long permitId, @Valid @RequestBody PermitReason body, CurrentUser user) {
        return svc.changeStatus(permitId, "SUSPENDED", body.reason(), user);
    }

    @PostMapping("/permits/{permitId}/reinstate")
    @Requires("permit.manage")
    public Permit reinstate(@PathVariable Long permitId, @Valid @RequestBody PermitReason body, CurrentUser user) {
        return svc.changeStatus(permitId, "ACTIVE", body.reason(), user);
    }

    @PostMapping("/permits/{permitId}/cancel")
    @Requires("permit.manage")
    public Permit cancel(@PathVariable Long permitId, @Valid @RequestBody PermitReason body, CurrentUser user) {
        return svc.changeStatus(permitId, "CANCELLED", body.reason(), user);
    }
}
