package com.rto.web;

import com.rto.core.*;
import com.rto.dto.OrgDto.*;
import com.rto.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "employees")
public class EmployeeController {
    private final EmployeeService svc;

    public EmployeeController(EmployeeService svc) {
        this.svc = svc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("employee.manage")
    @Operation(summary = "Create an employee for an existing or new person, optionally with a first posting")
    public EmployeeOut create(@Valid @RequestBody EmployeeCreate body, CurrentUser user) {
        return svc.create(body, user);
    }

    @GetMapping
    @Requires("employee.view")
    public PageResponse<EmployeeOut> list(@RequestParam(name = "office_id", required = false) Long officeId,
                                          @RequestParam(name = "designation_id", required = false) Long designationId,
                                          @RequestParam(name = "is_active", required = false) Boolean isActive, PageParams p) {
        return svc.list(p, officeId, designationId, isActive);
    }

    @GetMapping("/{employeeId}")
    @Requires("employee.view")
    public EmployeeOut get(@PathVariable Long employeeId) {
        return svc.view(employeeId);
    }

    @PatchMapping("/{employeeId}")
    @Requires("employee.manage")
    @Operation(summary = "Change designation or activate/deactivate (deactivation ends the current posting)")
    public EmployeeOut update(@PathVariable Long employeeId, @Valid @RequestBody EmployeeUpdate body, CurrentUser user) {
        return svc.update(employeeId, body, user);
    }

    @GetMapping("/{employeeId}/postings")
    @Requires("employee.view")
    @Operation(summary = "Full posting history, newest first")
    public List<PostingOut> postings(@PathVariable Long employeeId) {
        return svc.postings(employeeId);
    }

    @PostMapping("/{employeeId}/postings")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("employee.manage")
    @Operation(summary = "Transfer: closes the current posting (posted_to) and opens a new one; history is kept")
    public PostingOut addPosting(@PathVariable Long employeeId, @Valid @RequestBody PostingCreate body, CurrentUser user) {
        return svc.addPosting(employeeId, body, user);
    }
}
