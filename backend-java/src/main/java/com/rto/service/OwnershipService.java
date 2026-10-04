package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.VehicleDto.TransferCreate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static com.rto.core.Audit.m;

/**
 * Ownership transfers. Approval is ONE transaction: any failure rolls back the whole thing, so the vehicle can never
 * end up with two current owners, none, or a half-completed transfer.
 */
@Service
@Transactional
public class OwnershipService {
    private final Db db;
    private final Audit audit;
    private final ApplicationService apps;
    private final VehicleService vehicles;
    private final Notifications notifications;

    public OwnershipService(Db db, Audit audit, ApplicationService apps, VehicleService vehicles, Notifications notifications) {
        this.db = db;
        this.audit = audit;
        this.apps = apps;
        this.vehicles = vehicles;
        this.notifications = notifications;
    }

    public OwnershipTransfer create(TransferCreate d, CurrentUser user) {
        Vehicle vehicle = db.lock(Vehicle.class, d.vehicleId(), "Vehicle");   // serialises competing requests
        if (!"ACTIVE".equals(vehicle.getStatus())) {
            throw ApiException.conflict("VEHICLE_NOT_ACTIVE", "Only ACTIVE vehicles can be transferred (this one is " + vehicle.getStatus() + ")");
        }
        VehicleOwnership owner = vehicles.currentOwnership(vehicle.getVehicleId(), false);
        if (owner == null) throw ApiException.conflict("NO_CURRENT_OWNER", "The vehicle has no current owner to transfer from");
        if (d.fromCitizenId() != null && !d.fromCitizenId().equals(owner.getCitizenId())) {
            throw ApiException.conflict("NOT_CURRENT_OWNER", "from_citizen_id is not the vehicle's current owner");
        }
        if (d.toCitizenId().equals(owner.getCitizenId())) throw ApiException.conflict("SAME_OWNER", "The vehicle already belongs to this citizen");
        Citizen to = db.get(Citizen.class, d.toCitizenId(), "Transferee citizen");
        if (Boolean.TRUE.equals(to.getBlacklisted())) throw ApiException.conflict("CITIZEN_BLACKLISTED", "The transferee is blacklisted");
        Application app = apps.get(d.applicationId(), false);
        if (Set_of("REJECTED", "CANCELLED").contains(app.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_CLOSED", "The application is " + app.getCurrentStatus());
        }
        if (db.exists("select t.transferId from OwnershipTransfer t where t.vehicleId = :v and t.status = 'PENDING'", "v", vehicle.getVehicleId())) {
            throw ApiException.conflict("TRANSFER_PENDING", "A transfer is already pending for this vehicle");
        }
        OwnershipTransfer t = new OwnershipTransfer();
        t.setVehicleId(vehicle.getVehicleId());
        t.setFromCitizenId(owner.getCitizenId());
        t.setToCitizenId(d.toCitizenId());
        t.setApplicationId(d.applicationId());
        t.setRequestedAt(Clock.now());
        t.setStatus("PENDING");
        db.save(t);
        audit.record("ownership_transfers", t.getTransferId(), "INSERT", null,
                m("vehicle_id", vehicle.getVehicleId(), "from_citizen_id", owner.getCitizenId(), "to_citizen_id", d.toCitizenId(), "status", "PENDING"), user.userId());
        db.flush();
        return t;
    }

    private static java.util.Set<String> Set_of(String... v) {
        return java.util.Set.of(v);
    }

    public OwnershipTransfer approve(Long transferId, LocalDate transferDate, CurrentUser user) {
        OwnershipTransfer t = db.lock(OwnershipTransfer.class, transferId, "Ownership transfer");
        if (!"PENDING".equals(t.getStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only PENDING transfers can be approved (this one is " + t.getStatus() + ")");
        }
        Vehicle vehicle = db.lock(Vehicle.class, t.getVehicleId(), "Vehicle");
        if (!"ACTIVE".equals(vehicle.getStatus())) {
            throw ApiException.conflict("VEHICLE_NOT_ACTIVE", "The vehicle is " + vehicle.getStatus() + "; the transfer cannot be completed");
        }
        VehicleOwnership owner = vehicles.currentOwnership(t.getVehicleId(), true);
        if (owner == null) throw ApiException.conflict("NO_CURRENT_OWNER", "The vehicle has no current owner");
        if (!owner.getCitizenId().equals(t.getFromCitizenId())) {
            throw ApiException.conflict("NOT_CURRENT_OWNER", "The transferor is no longer the vehicle's current owner");
        }
        Citizen to = db.get(Citizen.class, t.getToCitizenId(), "Transferee citizen");
        if (Boolean.TRUE.equals(to.getBlacklisted())) throw ApiException.conflict("CITIZEN_BLACKLISTED", "The transferee is blacklisted");
        LocalDate when = transferDate != null ? transferDate : Clock.today();
        if (when.isAfter(Clock.today())) throw ApiException.badRequest("INVALID_DATES", "transfer_date cannot be in the future");
        if (when.isBefore(owner.getEffectiveFrom())) {
            throw ApiException.conflict("OWNERSHIP_CHRONOLOGY", "transfer_date precedes the current ownership start (" + owner.getEffectiveFrom() + ")");
        }
        owner.setEffectiveTo(when);   // 1. close the current ownership (the history row is kept)
        db.flush();                   //    must hit the DB before the new open row (uq_vehicle_current_owner)
        VehicleOwnership next = new VehicleOwnership();
        next.setVehicleId(t.getVehicleId());
        next.setCitizenId(t.getToCitizenId());
        next.setEffectiveFrom(when);
        db.save(next);                // 2. open the new ownership
        t.setStatus("APPROVED");      // 3. mark the transfer
        t.setApprovedAt(Clock.now());
        audit.record("ownership_transfers", transferId, "UPDATE", m("status", "PENDING"),
                m("status", "APPROVED", "transfer_date", when, "from_citizen_id", t.getFromCitizenId(), "to_citizen_id", t.getToCitizenId(),
                        "closed_ownership_id", owner.getOwnershipId(), "new_ownership_id", next.getOwnershipId()), user.userId());
        audit.record("vehicle_ownerships", next.getOwnershipId(), "INSERT", null,
                m("vehicle_id", t.getVehicleId(), "citizen_id", t.getToCitizenId(), "effective_from", when), user.userId());
        db.flush();                   // 4. all-or-nothing: the surrounding transaction commits or rolls back as one
        Citizen from = db.get(Citizen.class, t.getFromCitizenId(), "Citizen");
        notifications.notify(from.getPersonId(), "Ownership transfer approved", "Vehicle " + vehicle.getRegistrationNumber() + " has been transferred from you.");
        notifications.notify(to.getPersonId(), "Ownership transfer approved", "Vehicle " + vehicle.getRegistrationNumber() + " is now registered in your name.");
        return t;
    }

    public OwnershipTransfer reject(Long transferId, String reason, CurrentUser user) {
        OwnershipTransfer t = db.lock(OwnershipTransfer.class, transferId, "Ownership transfer");
        if (!"PENDING".equals(t.getStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only PENDING transfers can be rejected (this one is " + t.getStatus() + ")");
        }
        t.setStatus("REJECTED");   // ownership rows are deliberately untouched
        audit.record("ownership_transfers", transferId, "UPDATE", m("status", "PENDING"), m("status", "REJECTED", "reason", reason), user.userId());
        db.flush();
        Citizen to = db.get(Citizen.class, t.getToCitizenId(), "Citizen");
        notifications.notify(to.getPersonId(), "Ownership transfer rejected", "Transfer request rejected: " + reason);
        return t;
    }

    @Transactional(readOnly = true)
    public OwnershipTransfer get(Long id) {
        return db.get(OwnershipTransfer.class, id, "Ownership transfer");
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnershipTransfer> list(PageParams p, Long vehicleId, String status, Long citizenId) {
        QB q = new QB("t", "OwnershipTransfer t").eq("t.vehicleId", vehicleId).eq("t.status", status);
        if (citizenId != null) q.and("(t.fromCitizenId = :cid or t.toCitizenId = :cid)", "cid", citizenId);
        return db.page(q, OwnershipTransfer.class, p, Map.of("requested_at", "t.requestedAt", "transfer_id", "t.transferId"), "t.transferId", true);
    }
}
