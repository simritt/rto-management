package com.rto.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.Address;
import com.rto.dto.IdentityDto.*;
import com.rto.service.CitizenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/citizens")
@Tag(name = "citizens")
public class CitizenController {
    private final CitizenService svc;

    public CitizenController(CitizenService svc) {
        this.svc = svc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("citizen.create")
    @Operation(summary = "Register a citizen (person + citizen + optional address)")
    public CitizenOut create(@Valid @RequestBody CitizenCreate body, CurrentUser user) {
        return svc.create(body, user);
    }

    @GetMapping
    @Requires("citizen.view")
    @Operation(summary = "Search citizens by code, name or phone (national ID filter needs citizen.view_sensitive)")
    public PageResponse<CitizenListItem> list(@RequestParam(required = false) Boolean blacklisted,
                                              @RequestParam(required = false) @Size(max = 15) String phone,
                                              @RequestParam(name = "citizen_code", required = false) @Size(max = 20) String citizenCode,
                                              @RequestParam(name = "national_id", required = false) @Size(max = 20) String nationalId,
                                              PageParams p, CurrentUser user) {
        return svc.list(user, p, blacklisted, phone, citizenCode, nationalId);
    }

    @GetMapping("/me")
    @Operation(summary = "The citizen record of the authenticated user")
    public CitizenOut me(CurrentUser user) {
        return svc.me(user);
    }

    @GetMapping("/{citizenId}")
    public CitizenOut get(@PathVariable Long citizenId, CurrentUser user) {
        return svc.view(user, citizenId);
    }

    @PatchMapping("/{citizenId}")
    @Requires("citizen.update")
    public CitizenOut update(@PathVariable Long citizenId, @RequestBody JsonNode body, CurrentUser user) {
        return svc.update(citizenId, body, user);
    }

    @GetMapping("/{citizenId}/addresses")
    @Operation(summary = "Full address history (newest first); is_current marks the active rows")
    public List<Address> addresses(@PathVariable Long citizenId,
                                   @RequestParam(name = "current_only", defaultValue = "false") boolean currentOnly, CurrentUser user) {
        return svc.addresses(user, citizenId, currentOnly);
    }

    @PostMapping("/{citizenId}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("citizen.update")
    @Operation(summary = "Add an address; the previous current address of the same type is closed, not overwritten")
    public Address addAddress(@PathVariable Long citizenId, @Valid @RequestBody AddressCreate body, CurrentUser user) {
        return svc.addAddress(citizenId, body, user);
    }
}
