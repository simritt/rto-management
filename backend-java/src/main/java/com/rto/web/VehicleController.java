package com.rto.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.OwnershipTransfer;
import com.rto.dto.VehicleDto.*;
import com.rto.service.OwnershipService;
import com.rto.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "vehicles")
public class VehicleController {
    private final VehicleService svc;
    private final OwnershipService transfers;

    public VehicleController(VehicleService svc, OwnershipService transfers) {
        this.svc = svc;
        this.transfers = transfers;
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("vehicle.create")
    @Operation(summary = "Register a vehicle (owner, if given, becomes the first vehicle_ownerships row)")
    public VehicleOut create(@Valid @RequestBody VehicleCreate body, CurrentUser user) {
        return svc.create(body, user);
    }

    @GetMapping("/vehicles")
    @Requires("vehicle.view")
    public PageResponse<VehicleOut> list(@RequestParam(name = "registration_number", required = false) @Size(max = 20) String reg,
                                         @RequestParam(name = "chassis_number", required = false) @Size(max = 40) String chassis,
                                         @RequestParam(name = "engine_number", required = false) @Size(max = 40) String engine,
                                         @RequestParam(name = "manufacturer_id", required = false) Long manufacturerId,
                                         @RequestParam(name = "model_id", required = false) Long modelId,
                                         @RequestParam(name = "vehicle_type_id", required = false) Long vehicleTypeId,
                                         @RequestParam(name = "fuel_type_id", required = false) Long fuelTypeId,
                                         @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]+$") String status,
                                         @RequestParam(name = "office_id", required = false) Long officeId,
                                         @RequestParam(name = "owner_citizen_id", required = false) Long ownerCitizenId, PageParams p) {
        return svc.list(p, reg, chassis, engine, manufacturerId, modelId, vehicleTypeId, fuelTypeId, status, officeId, ownerCitizenId);
    }

    @GetMapping("/vehicles/{vehicleId}")
    @Requires("vehicle.view")
    public VehicleOut get(@PathVariable Long vehicleId) {
        return svc.view(vehicleId);
    }

    @PatchMapping("/vehicles/{vehicleId}")
    @Requires("vehicle.update")
    @Operation(summary = "Update colour or status (validated transitions, reason required, audited)")
    public VehicleOut update(@PathVariable Long vehicleId, @RequestBody JsonNode body, CurrentUser user) {
        return svc.update(vehicleId, body, user);
    }

    @GetMapping("/vehicles/{vehicleId}/owners")
    @Requires("vehicle.view")
    @Operation(summary = "Full ownership history, newest first")
    public List<OwnershipOut> owners(@PathVariable Long vehicleId) {
        return svc.owners(vehicleId);
    }

    @GetMapping("/vehicles/{vehicleId}/current-owner")
    @Requires("vehicle.view")
    public OwnershipOut currentOwner(@PathVariable Long vehicleId) {
        return svc.currentOwner(vehicleId);
    }

    @PostMapping("/vehicles/{vehicleId}/owners")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("vehicle.update")
    @Operation(summary = "Record the FIRST owner of an ownerless vehicle (later changes use ownership transfers)")
    public OwnershipOut addOwner(@PathVariable Long vehicleId, @Valid @RequestBody AddOwner body, CurrentUser user) {
        return svc.addInitialOwner(vehicleId, body.citizenId(), body.effectiveFrom(), user);
    }

    @GetMapping("/citizens/{citizenId}/vehicles")
    @Operation(summary = "Vehicles currently owned by a citizen")
    public List<Map<String, Object>> citizenVehicles(@PathVariable Long citizenId,
                                                     @RequestParam(name = "include_history", defaultValue = "false") boolean includeHistory, CurrentUser user) {
        if (!(user.has("vehicle.view") || user.isCitizen(citizenId))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: vehicle.view");
        }
        return svc.vehiclesOfCitizen(citizenId, includeHistory);
    }

    // ---- ownership transfers ---------------------------------------------------------------------------------

    @PostMapping("/ownership-transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("vehicle.transfer")
    public OwnershipTransfer createTransfer(@Valid @RequestBody TransferCreate body, CurrentUser user) {
        return transfers.create(body, user);
    }

    @GetMapping("/ownership-transfers")
    @Requires("vehicle.view")
    public PageResponse<OwnershipTransfer> transfers(@RequestParam(name = "vehicle_id", required = false) Long vehicleId,
                                                     @RequestParam(required = false) @Pattern(regexp = "^[A-Z]+$") String status,
                                                     @RequestParam(name = "citizen_id", required = false) Long citizenId, PageParams p) {
        return transfers.list(p, vehicleId, status, citizenId);
    }

    @GetMapping("/ownership-transfers/{transferId}")
    @Requires("vehicle.view")
    public OwnershipTransfer transfer(@PathVariable Long transferId) {
        return transfers.get(transferId);
    }

    @PostMapping("/ownership-transfers/{transferId}/approve")
    @Requires("vehicle.transfer_approve")
    @Operation(summary = "Atomically close the old ownership, open the new one and mark the transfer APPROVED")
    public OwnershipTransfer approve(@PathVariable Long transferId, @RequestBody(required = false) TransferApprove body, CurrentUser user) {
        return transfers.approve(transferId, body == null ? null : body.transferDate(), user);
    }

    @PostMapping("/ownership-transfers/{transferId}/reject")
    @Requires("vehicle.transfer_approve")
    @Operation(summary = "Reject; vehicle ownership is not changed")
    public OwnershipTransfer reject(@PathVariable Long transferId, @Valid @RequestBody TransferReject body, CurrentUser user) {
        return transfers.reject(transferId, body.reason(), user);
    }
}
