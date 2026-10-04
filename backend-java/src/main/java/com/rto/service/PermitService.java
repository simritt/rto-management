package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.PermitDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rto.core.Audit.m;

@Service
@Transactional
public class PermitService {
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "ACTIVE", Set.of("SUSPENDED", "CANCELLED", "EXPIRED"),
            "SUSPENDED", Set.of("ACTIVE", "CANCELLED", "EXPIRED"),
            "EXPIRED", Set.of(),      // a lapsed permit is replaced by issuing a new one
            "CANCELLED", Set.of());

    private final Db db;
    private final Audit audit;
    private final ApplicationService apps;
    private final Notifications notifications;

    public PermitService(Db db, Audit audit, ApplicationService apps, Notifications notifications) {
        this.db = db;
        this.audit = audit;
        this.apps = apps;
        this.notifications = notifications;
    }

    // ---- routes ---------------------------------------------------------------------------------------------

    public Route createRoute(RouteCreate d, CurrentUser user) {
        Route r = new Route();
        r.setRouteName(d.routeName());
        r.setOrigin(d.origin());
        r.setDestination(d.destination());
        db.save(r);
        audit.record("routes", r.getRouteId(), "INSERT", null, m("route_name", d.routeName(), "origin", d.origin(), "destination", d.destination()), user.userId());
        db.flush();
        return r;
    }

    @Transactional(readOnly = true)
    public List<RouteSegment> segments(Long routeId) {
        db.get(Route.class, routeId, "Route");
        return db.list(RouteSegment.class, "select s from RouteSegment s where s.routeId = :r order by s.sequenceNo", "r", routeId);
    }

    @Transactional(readOnly = true)
    public RouteDetail route(Long id) {
        Route r = db.get(Route.class, id, "Route");
        return new RouteDetail(r.getRouteId(), r.getRouteName(), r.getOrigin(), r.getDestination(), segments(id));
    }

    public RouteSegment addSegment(Long routeId, SegmentCreate d, CurrentUser user) {
        db.lock(Route.class, routeId, "Route");   // serialise auto-numbering per route
        int seq;
        if (d.sequenceNo() == null) {
            Integer max = db.first(Integer.class, "select max(s.sequenceNo) from RouteSegment s where s.routeId = :r", "r", routeId);
            seq = (max == null ? 0 : max) + 1;
        } else {
            seq = d.sequenceNo();
            if (db.exists("select s.segmentId from RouteSegment s where s.routeId = :r and s.sequenceNo = :n", "r", routeId, "n", seq)) {
                throw ApiException.conflict("DUPLICATE", "Sequence number " + seq + " is already used on this route");
            }
        }
        RouteSegment s = new RouteSegment();
        s.setRouteId(routeId);
        s.setSequenceNo(seq);
        s.setSegmentName(d.segmentName());
        db.save(s);
        audit.record("route_segments", s.getSegmentId(), "INSERT", null, m("route_id", routeId, "sequence_no", seq, "segment_name", d.segmentName()), user.userId());
        db.flush();
        return s;
    }

    @Transactional(readOnly = true)
    public PageResponse<Route> routes(PageParams p) {
        QB q = new QB("r", "Route r").search(p.search(), "r.routeName", "r.origin", "r.destination");
        return db.page(q, Route.class, p, Map.of("route_name", "r.routeName", "route_id", "r.routeId"), "r.routeId", false);
    }

    // ---- permits ---------------------------------------------------------------------------------------------

    private void history(Long permitId, String prev, String next, String reason) {
        PermitStatusHistory h = new PermitStatusHistory();
        h.setPermitId(permitId);
        h.setPreviousStatus(prev);
        h.setNewStatus(next);
        h.setReason(reason);
        db.save(h);
    }

    public Permit create(PermitCreate d, CurrentUser user) {
        if (d.issueDate() != null && d.expiryDate() != null && !d.expiryDate().isAfter(d.issueDate())) {
            throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", "expiry_date", "message", "expiry_date must be after issue_date")));
        }
        Vehicle vehicle = db.lock(Vehicle.class, d.vehicleId(), "Vehicle");
        if (!"ACTIVE".equals(vehicle.getStatus())) {
            throw ApiException.conflict("VEHICLE_NOT_ACTIVE", "Permits can only be issued to ACTIVE vehicles (this one is " + vehicle.getStatus() + ")");
        }
        if (!Boolean.TRUE.equals(db.get(VehicleType.class, vehicle.getVehicleTypeId(), "Vehicle type").getIsCommercial())) {
            throw ApiException.conflict("VEHICLE_NOT_COMMERCIAL", "Permits are issued only to commercial vehicles");
        }
        Citizen citizen = db.get(Citizen.class, d.citizenId(), "Operator citizen");
        if (Boolean.TRUE.equals(citizen.getBlacklisted())) throw ApiException.conflict("CITIZEN_BLACKLISTED", "The operator is blacklisted");
        PermitType ptype = db.get(PermitType.class, d.permitTypeId(), "Permit type");
        if (d.routeId() != null) db.get(Route.class, d.routeId(), "Route");
        Application app = apps.get(d.applicationId(), true);
        if (!apps.applicantOf(app).getCitizenId().equals(d.citizenId())) {
            throw ApiException.conflict("APPLICATION_CITIZEN_MISMATCH", "The application belongs to a different citizen");
        }
        if (!Set.of("APPROVED", "COMPLETED").contains(app.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_NOT_APPROVED", "The application must be APPROVED (it is " + app.getCurrentStatus() + ")");
        }
        var issue = d.issueDate() != null ? d.issueDate() : Clock.today();
        var expiry = d.expiryDate() != null ? d.expiryDate() : issue.plusMonths(ptype.getValidityMonths());
        if (!expiry.isAfter(issue)) throw ApiException.badRequest("INVALID_DATES", "expiry_date must be after issue_date");
        String clash = db.first(String.class, "select p.permitNumber from Permit p where p.vehicleId = :v and p.permitTypeId = :t "
                + "and p.status in ('ACTIVE','SUSPENDED') and p.expiryDate >= :i", "v", d.vehicleId(), "t", d.permitTypeId(), "i", issue);
        if (clash != null) throw ApiException.conflict("PERMIT_EXISTS", "The vehicle already holds a live " + ptype.getTypeName() + " permit (" + clash + ")");
        String number = d.permitNumber() != null ? d.permitNumber() : Common.genNumber("PRM");
        if (db.exists("select p.permitId from Permit p where p.permitNumber = :n", "n", number)) throw ApiException.conflict("DUPLICATE", "Permit number already exists");

        Permit p = new Permit();
        p.setPermitNumber(number);
        p.setVehicleId(d.vehicleId());
        p.setCitizenId(d.citizenId());
        p.setPermitTypeId(d.permitTypeId());
        p.setRouteId(d.routeId());
        p.setApplicationId(d.applicationId());
        p.setIssueDate(issue);
        p.setExpiryDate(expiry);
        p.setStatus("ACTIVE");
        db.save(p);
        history(p.getPermitId(), null, "ACTIVE", "Permit issued");
        audit.record("permits", p.getPermitId(), "INSERT", null, m("permit_number", number, "vehicle_id", d.vehicleId(), "citizen_id", d.citizenId(),
                "issue_date", issue, "expiry_date", expiry), user.userId());
        if ("APPROVED".equals(app.getCurrentStatus())) apps.applyStatus(app, "COMPLETED", user.userId(), "Permit " + number + " issued");
        db.flush();
        notifications.notify(citizen.getPersonId(), "Permit issued", "Permit " + number + " is valid until " + expiry + ".");
        return p;
    }

    public Permit changeStatus(Long id, String next, String reason, CurrentUser user) {
        Permit p = db.lock(Permit.class, id, "Permit");
        String prev = p.getStatus();
        Common.checkTransition(TRANSITIONS, prev, next, "permit status");
        if ("ACTIVE".equals(next) && p.getExpiryDate().isBefore(Clock.today())) {
            throw ApiException.conflict("PERMIT_EXPIRED", "The permit has expired and cannot be reinstated");
        }
        p.setStatus(next);
        history(id, prev, next, reason);
        audit.record("permits", id, "UPDATE", m("status", prev), m("status", next, "reason", reason), user.userId());
        db.flush();
        Citizen citizen = db.get(Citizen.class, p.getCitizenId(), "Citizen");
        notifications.notify(citizen.getPersonId(), "Permit " + next.charAt(0) + next.substring(1).toLowerCase(),
                "Permit " + p.getPermitNumber() + " is now " + next + ". Reason: " + reason);
        return p;
    }

    public Map<String, Object> expireOverdue(CurrentUser user) {
        int n = 0;
        for (Permit p : db.lockList(Permit.class, "select p from Permit p where p.status in ('ACTIVE','SUSPENDED') and p.expiryDate < :t", "t", Clock.today())) {
            String prev = p.getStatus();
            p.setStatus("EXPIRED");
            history(p.getPermitId(), prev, "EXPIRED", "Expired on schedule");
            audit.record("permits", p.getPermitId(), "UPDATE", m("status", prev), m("status", "EXPIRED"), user.userId());
            n++;
        }
        db.flush();
        return m("permits_expired", n);
    }

    @Transactional(readOnly = true)
    public Permit get(Long id) {
        return db.get(Permit.class, id, "Permit");
    }

    @Transactional(readOnly = true)
    public List<PermitStatusHistory> history(Long id) {
        get(id);
        return db.list(PermitStatusHistory.class, "select h from PermitStatusHistory h where h.permitId = :p order by h.historyId", "p", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<Permit> list(PageParams p, Long vehicleId, Long citizenId, String status, Long permitTypeId, Long routeId, Integer expiringWithinDays) {
        QB q = new QB("p", "Permit p").search(p.search(), "p.permitNumber").eq("p.vehicleId", vehicleId).eq("p.citizenId", citizenId)
                .eq("p.permitTypeId", permitTypeId).eq("p.routeId", routeId).eq("p.status", status);
        if (expiringWithinDays != null) q.and("p.status = 'ACTIVE' and p.expiryDate between :ea and :eb", "ea", Clock.today(), "eb", Clock.today().plusDays(expiringWithinDays));
        return db.page(q, Permit.class, p, Map.of("issue_date", "p.issueDate", "expiry_date", "p.expiryDate", "permit_number", "p.permitNumber",
                "permit_id", "p.permitId"), "p.permitId", true);
    }
}
