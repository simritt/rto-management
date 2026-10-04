package com.rto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.VehicleDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static com.rto.core.Audit.m;

/** Vehicle master data and effective-dated ownership history (read side + initial ownership). */
@Service
@Transactional
public class VehicleService {
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "ACTIVE", Set.of("BLACKLISTED", "SCRAPPED", "DEREGISTERED"),
            "BLACKLISTED", Set.of("ACTIVE", "SCRAPPED", "DEREGISTERED"),
            "SCRAPPED", Set.of(), "DEREGISTERED", Set.of());

    private final Db db;
    private final Audit audit;
    private final Common common;
    private final Patch patch;
    private final ObjectMapper mapper;

    public VehicleService(Db db, Audit audit, Common common, Patch patch, ObjectMapper mapper) {
        this.db = db;
        this.audit = audit;
        this.common = common;
        this.patch = patch;
        this.mapper = mapper;
    }

    public static String norm(String v) {
        return String.join(" ", v.trim().toUpperCase().split("\\s+"));
    }

    public VehicleOwnership currentOwnership(Long vehicleId, boolean lock) {
        String q = "select o from VehicleOwnership o where o.vehicleId = :v and o.effectiveTo is null";
        return lock ? db.lockFirst(VehicleOwnership.class, q, "v", vehicleId) : db.first(VehicleOwnership.class, q, "v", vehicleId);
    }

    private OwnerBrief ownerBrief(VehicleOwnership o) {
        if (o == null) return null;
        Citizen c = db.get(Citizen.class, o.getCitizenId(), "Citizen");
        Person p = db.get(Person.class, c.getPersonId(), "Person");
        return new OwnerBrief(c.getCitizenId(), c.getCitizenCode(), p.getFirstName() + " " + p.getLastName(), o.getEffectiveFrom());
    }

    public VehicleOut out(Vehicle v, boolean withOwner) {
        return new VehicleOut(v.getVehicleId(), v.getRegistrationNumber(), v.getChassisNumber(), v.getEngineNumber(),
                v.getManufacturerId(), db.get(VehicleManufacturer.class, v.getManufacturerId(), "Manufacturer").getName(),
                v.getModelId(), db.get(VehicleModel.class, v.getModelId(), "Vehicle model").getModelName(),
                v.getVehicleTypeId(), db.get(VehicleType.class, v.getVehicleTypeId(), "Vehicle type").getTypeName(),
                v.getFuelTypeId(), db.get(FuelType.class, v.getFuelTypeId(), "Fuel type").getFuelName(),
                v.getManufactureYear(), v.getColor(), v.getRegisteringOfficeId(), v.getRegistrationDate(), v.getStatus(),
                withOwner ? ownerBrief(currentOwnership(v.getVehicleId(), false)) : null);
    }

    public Vehicle get(Long id, boolean lock) {
        return lock ? db.lock(Vehicle.class, id, "Vehicle") : db.get(Vehicle.class, id, "Vehicle");
    }

    @Transactional(readOnly = true)
    public VehicleOut view(Long id) {
        return out(get(id, false), true);
    }

    private VehicleOwnership openOwnership(Long vehicleId, Long citizenId, LocalDate from) {
        VehicleOwnership o = new VehicleOwnership();
        o.setVehicleId(vehicleId);
        o.setCitizenId(citizenId);
        o.setEffectiveFrom(from);
        return db.save(o);   // uq_vehicle_current_owner rejects a second open row
    }

    public VehicleOut create(VehicleCreate d, CurrentUser user) {
        common.requireActiveOffice(d.registeringOfficeId());
        String reg = norm(d.registrationNumber()), chassis = norm(d.chassisNumber()), engine = norm(d.engineNumber());
        if (db.exists("select v.vehicleId from Vehicle v where v.registrationNumber = :x", "x", reg)) throw ApiException.conflict("DUPLICATE", "Registration number already exists");
        if (db.exists("select v.vehicleId from Vehicle v where v.chassisNumber = :x", "x", chassis)) throw ApiException.conflict("DUPLICATE", "Chassis number already exists");
        if (db.exists("select v.vehicleId from Vehicle v where v.engineNumber = :x", "x", engine)) throw ApiException.conflict("DUPLICATE", "Engine number already exists");
        db.get(VehicleManufacturer.class, d.manufacturerId(), "Manufacturer");
        VehicleModel model = db.get(VehicleModel.class, d.modelId(), "Vehicle model");
        if (!model.getManufacturerId().equals(d.manufacturerId())) {
            throw ApiException.conflict("MODEL_MANUFACTURER_MISMATCH", "The model does not belong to the given manufacturer");
        }
        db.get(VehicleType.class, d.vehicleTypeId(), "Vehicle type");
        db.get(FuelType.class, d.fuelTypeId(), "Fuel type");
        if (d.manufactureYear() > Clock.today().getYear() + 1) throw ApiException.badRequest("INVALID_YEAR", "manufacture_year is in the future");
        LocalDate regDate = d.registrationDate() != null ? d.registrationDate() : Clock.today();
        if (regDate.isAfter(Clock.today())) throw ApiException.badRequest("INVALID_DATES", "registration_date cannot be in the future");
        Citizen owner = null;
        if (d.ownerCitizenId() != null) {
            owner = db.get(Citizen.class, d.ownerCitizenId(), "Owner citizen");
            if (Boolean.TRUE.equals(owner.getBlacklisted())) throw ApiException.conflict("CITIZEN_BLACKLISTED", "A blacklisted citizen cannot be registered as owner");
        }
        Vehicle v = new Vehicle();
        v.setRegistrationNumber(reg);
        v.setChassisNumber(chassis);
        v.setEngineNumber(engine);
        v.setManufacturerId(d.manufacturerId());
        v.setModelId(d.modelId());
        v.setVehicleTypeId(d.vehicleTypeId());
        v.setFuelTypeId(d.fuelTypeId());
        v.setManufactureYear(d.manufactureYear());
        v.setColor(d.color());
        v.setRegisteringOfficeId(d.registeringOfficeId());
        v.setRegistrationDate(regDate);
        v.setStatus("ACTIVE");
        db.save(v);
        if (owner != null) openOwnership(v.getVehicleId(), owner.getCitizenId(), regDate);
        audit.record("vehicles", v.getVehicleId(), "INSERT", null,
                m("registration_number", reg, "registering_office_id", d.registeringOfficeId(), "owner_citizen_id", d.ownerCitizenId(), "status", "ACTIVE"), user.userId());
        db.flush();
        return out(v, true);
    }

    public VehicleOut update(Long id, JsonNode body, CurrentUser user) {
        Patch.Parsed<VehicleUpdate> pr = patch.parse(body, VehicleUpdate.class);
        VehicleUpdate d = pr.dto();
        Vehicle v = get(id, true);
        Map<String, Object> old = m(), neu = m();
        if (pr.has("color") && !Objects.equals(d.color(), v.getColor())) {
            old.put("color", v.getColor());
            neu.put("color", d.color());
            v.setColor(d.color());
        }
        if (d.status() != null && !d.status().equals(v.getStatus())) {
            if (d.reason() == null || d.reason().isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "A reason is required when changing a vehicle's status");
            Common.checkTransition(TRANSITIONS, v.getStatus(), d.status(), "vehicle status");
            if (Set.of("SCRAPPED", "DEREGISTERED").contains(d.status())
                    && db.exists("select t.transferId from OwnershipTransfer t where t.vehicleId = :v and t.status = 'PENDING'", "v", id)) {
                throw ApiException.conflict("TRANSFER_PENDING", "The vehicle has a pending ownership transfer");
            }
            old.put("status", v.getStatus());
            neu.put("status", d.status());
            neu.put("reason", d.reason());
            v.setStatus(d.status());
        }
        if (!neu.isEmpty()) audit.record("vehicles", id, "UPDATE", old, neu, user.userId());
        db.flush();
        return out(v, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleOut> list(PageParams p, String registrationNumber, String chassisNumber, String engineNumber,
                                         Long manufacturerId, Long modelId, Long vehicleTypeId, Long fuelTypeId, String status,
                                         Long officeId, Long ownerCitizenId) {
        QB q = new QB("v", "Vehicle v");
        if (p.search() != null) q.search(p.search().toUpperCase(), "v.registrationNumber", "v.chassisNumber", "v.engineNumber");
        if (registrationNumber != null) q.search(registrationNumber.toUpperCase(), "v.registrationNumber");
        if (chassisNumber != null) q.search(chassisNumber.toUpperCase(), "v.chassisNumber");
        if (engineNumber != null) q.search(engineNumber.toUpperCase(), "v.engineNumber");
        q.eq("v.manufacturerId", manufacturerId).eq("v.modelId", modelId).eq("v.vehicleTypeId", vehicleTypeId)
                .eq("v.fuelTypeId", fuelTypeId).eq("v.registeringOfficeId", officeId).eq("v.status", status);
        if (ownerCitizenId != null) {
            q.and("v.vehicleId in (select o.vehicleId from VehicleOwnership o where o.citizenId = :own and o.effectiveTo is null)", "own", ownerCitizenId);
        }
        return db.page(q, Vehicle.class, p, Map.of("registration_number", "v.registrationNumber", "registration_date", "v.registrationDate",
                "manufacture_year", "v.manufactureYear", "vehicle_id", "v.vehicleId", "status", "v.status"), "v.vehicleId", true)
                .map(v -> out(v, true));
    }

    // ---- ownership -------------------------------------------------------------------------------------------

    public OwnershipOut ownershipOut(VehicleOwnership o) {
        Citizen c = db.get(Citizen.class, o.getCitizenId(), "Citizen");
        Person p = db.get(Person.class, c.getPersonId(), "Person");
        return new OwnershipOut(o.getOwnershipId(), o.getVehicleId(), o.getCitizenId(), c.getCitizenCode(),
                p.getFirstName() + " " + p.getLastName(), o.getEffectiveFrom(), o.getEffectiveTo(), o.getEffectiveTo() == null);
    }

    @Transactional(readOnly = true)
    public List<OwnershipOut> owners(Long vehicleId) {
        get(vehicleId, false);
        return db.list(VehicleOwnership.class, "select o from VehicleOwnership o where o.vehicleId = :v order by o.effectiveFrom desc, o.ownershipId desc",
                "v", vehicleId).stream().map(this::ownershipOut).toList();
    }

    @Transactional(readOnly = true)
    public OwnershipOut currentOwner(Long vehicleId) {
        get(vehicleId, false);
        VehicleOwnership o = currentOwnership(vehicleId, false);
        if (o == null) throw ApiException.notFound("NO_CURRENT_OWNER", "This vehicle has no current owner on record");
        return ownershipOut(o);
    }

    public OwnershipOut addInitialOwner(Long vehicleId, Long citizenId, LocalDate effectiveFrom, CurrentUser user) {
        get(vehicleId, true);
        if (currentOwnership(vehicleId, true) != null) throw ApiException.conflict("OWNER_EXISTS", "The vehicle already has a current owner; use an ownership transfer");
        Citizen citizen = db.get(Citizen.class, citizenId, "Citizen");
        if (Boolean.TRUE.equals(citizen.getBlacklisted())) throw ApiException.conflict("CITIZEN_BLACKLISTED", "A blacklisted citizen cannot be registered as owner");
        LocalDate start = effectiveFrom != null ? effectiveFrom : Clock.today();
        if (start.isAfter(Clock.today())) throw ApiException.badRequest("INVALID_DATES", "effective_from cannot be in the future");
        LocalDate lastEnd = db.first(LocalDate.class, "select max(o.effectiveTo) from VehicleOwnership o where o.vehicleId = :v", "v", vehicleId);
        if (lastEnd != null && start.isBefore(lastEnd)) throw ApiException.conflict("OWNERSHIP_CHRONOLOGY", "effective_from overlaps the previous ownership ending " + lastEnd);
        VehicleOwnership o = openOwnership(vehicleId, citizenId, start);
        audit.record("vehicle_ownerships", o.getOwnershipId(), "INSERT", null, m("vehicle_id", vehicleId, "citizen_id", citizenId, "effective_from", start), user.userId());
        db.flush();
        return ownershipOut(o);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> vehiclesOfCitizen(Long citizenId, boolean includeHistory) {
        db.get(Citizen.class, citizenId, "Citizen");
        List<Map<String, Object>> out = new ArrayList<>();
        for (VehicleOwnership o : db.list(VehicleOwnership.class, "select o from VehicleOwnership o where o.citizenId = :c"
                + (includeHistory ? "" : " and o.effectiveTo is null") + " order by o.effectiveFrom desc", "c", citizenId)) {
            @SuppressWarnings("unchecked") Map<String, Object> row = new LinkedHashMap<>(mapper.convertValue(out(get(o.getVehicleId(), false), false), Map.class));
            row.put("ownership_from", o.getEffectiveFrom());
            row.put("ownership_to", o.getEffectiveTo());
            out.add(row);
        }
        return out;
    }
}
