package com.rto.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.OrgDto.*;
import com.rto.service.OfficeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "offices")
public class OfficeController {
    private final OfficeService svc;

    public OfficeController(OfficeService svc) {
        this.svc = svc;
    }

    @PostMapping("/offices")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("office.manage")
    public RtoOffice createOffice(@Valid @RequestBody OfficeCreate body, CurrentUser user) {
        return svc.createOffice(body, user);
    }

    @GetMapping("/offices")
    public PageResponse<RtoOffice> offices(@RequestParam(name = "region_id", required = false) Long regionId,
                                           @RequestParam(name = "is_active", required = false) Boolean isActive, PageParams p) {
        return svc.offices(p, regionId, isActive);
    }

    @GetMapping("/offices/{officeId}")
    public RtoOffice office(@PathVariable Long officeId) {
        return svc.office(officeId);
    }

    @PatchMapping("/offices/{officeId}")
    @Requires("office.manage")
    @Operation(summary = "Update an office; set is_active=false to stop new operational assignments")
    public RtoOffice updateOffice(@PathVariable Long officeId, @Valid @RequestBody OfficeUpdate body, CurrentUser user) {
        return svc.updateOffice(officeId, body, user);
    }

    @GetMapping("/offices/{officeId}/counters")
    public List<Counter> counters(@PathVariable Long officeId) {
        return svc.counters(officeId);
    }

    @PostMapping("/offices/{officeId}/counters")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("office.manage")
    public Counter createCounter(@PathVariable Long officeId, @Valid @RequestBody CounterCreate body, CurrentUser user) {
        return svc.createCounter(officeId, body, user);
    }

    @PatchMapping("/counters/{counterId}")
    @Requires("office.manage")
    public Counter updateCounter(@PathVariable Long counterId, @RequestBody JsonNode body, CurrentUser user) {
        return svc.updateCounter(counterId, body, user);
    }

    @DeleteMapping("/counters/{counterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Requires("office.manage")
    public void deleteCounter(@PathVariable Long counterId, CurrentUser user) {
        svc.deleteCounter(counterId, user);
    }

    @GetMapping("/departments")
    public List<Department> departments() {
        return svc.departments();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("office.manage")
    public Department createDepartment(@Valid @RequestBody DepartmentIn body, CurrentUser user) {
        return svc.createDepartment(body, user);
    }

    @GetMapping("/designations")
    public List<Designation> designations() {
        return svc.designations();
    }

    @PostMapping("/designations")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("employee.manage")
    public Designation createDesignation(@Valid @RequestBody DesignationIn body, CurrentUser user) {
        return svc.createDesignation(body, user);
    }

    @PatchMapping("/designations/{designationId}")
    @Requires("employee.manage")
    public Designation updateDesignation(@PathVariable Long designationId, @Valid @RequestBody DesignationUpdate body, CurrentUser user) {
        return svc.updateDesignation(designationId, body, user);
    }
}
