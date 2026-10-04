package com.rto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.OrgDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.rto.core.Audit.m;

@Service
@Transactional
public class OfficeService {
    private final Db db;
    private final Audit audit;
    private final Patch patch;

    public OfficeService(Db db, Audit audit, Patch patch) {
        this.db = db;
        this.audit = audit;
        this.patch = patch;
    }

    // ---- offices ------------------------------------------------------------------------------------

    public RtoOffice createOffice(OfficeCreate d, CurrentUser actor) {
        db.get(Region.class, d.regionId(), "Region");
        if (db.exists("select o.officeId from RtoOffice o where o.officeCode = :c", "c", d.officeCode())) {
            throw ApiException.conflict("DUPLICATE", "Office code already exists");
        }
        RtoOffice o = new RtoOffice();
        o.setRegionId(d.regionId());
        o.setOfficeName(d.officeName());
        o.setOfficeCode(d.officeCode());
        o.setAddressLine(d.addressLine());
        o.setIsActive(d.isActive());
        db.save(o);
        audit.record("rto_offices", o.getOfficeId(), "INSERT", null, m("region_id", d.regionId(), "office_name", d.officeName(),
                "office_code", d.officeCode(), "address_line", d.addressLine(), "is_active", d.isActive()), actor.userId());
        db.flush();
        return o;
    }

    public RtoOffice updateOffice(Long id, OfficeUpdate d, CurrentUser actor) {
        RtoOffice o = db.lock(RtoOffice.class, id, "RTO office");
        Map<String, Object> old = m(), neu = m();
        if (d.regionId() != null && !d.regionId().equals(o.getRegionId())) {
            db.get(Region.class, d.regionId(), "Region");
            old.put("region_id", o.getRegionId());
            neu.put("region_id", d.regionId());
            o.setRegionId(d.regionId());
        }
        if (d.officeName() != null && !d.officeName().equals(o.getOfficeName())) {
            old.put("office_name", o.getOfficeName());
            neu.put("office_name", d.officeName());
            o.setOfficeName(d.officeName());
        }
        if (d.addressLine() != null && !d.addressLine().equals(o.getAddressLine())) {
            old.put("address_line", o.getAddressLine());
            neu.put("address_line", d.addressLine());
            o.setAddressLine(d.addressLine());
        }
        if (d.isActive() != null && !d.isActive().equals(o.getIsActive())) {
            old.put("is_active", o.getIsActive());
            neu.put("is_active", d.isActive());
            o.setIsActive(d.isActive());
        }
        if (!neu.isEmpty()) audit.record("rto_offices", id, "UPDATE", old, neu, actor.userId());
        db.flush();
        return o;
    }

    @Transactional(readOnly = true)
    public PageResponse<RtoOffice> offices(PageParams p, Long regionId, Boolean isActive) {
        QB q = new QB("o", "RtoOffice o").search(p.search(), "o.officeName", "o.officeCode")
                .eq("o.regionId", regionId).eq("o.isActive", isActive);
        return db.page(q, RtoOffice.class, p, Map.of("office_name", "o.officeName", "office_code", "o.officeCode",
                "office_id", "o.officeId"), "o.officeId", false);
    }

    @Transactional(readOnly = true)
    public RtoOffice office(Long id) {
        return db.get(RtoOffice.class, id, "RTO office");
    }

    // ---- counters -----------------------------------------------------------------------------------

    public Counter createCounter(Long officeId, CounterCreate d, CurrentUser actor) {
        db.get(RtoOffice.class, officeId, "RTO office");
        if (db.exists("select c.counterId from Counter c where c.officeId = :o and c.counterNumber = :n",
                "o", officeId, "n", d.counterNumber())) {
            throw ApiException.conflict("DUPLICATE", "Counter number already exists in this office");
        }
        Counter c = new Counter();
        c.setOfficeId(officeId);
        c.setCounterNumber(d.counterNumber());
        c.setServiceCategory(d.serviceCategory());
        db.save(c);
        audit.record("counters", c.getCounterId(), "INSERT", null,
                m("office_id", officeId, "counter_number", d.counterNumber(), "service_category", d.serviceCategory()), actor.userId());
        db.flush();
        return c;
    }

    public Counter updateCounter(Long id, JsonNode body, CurrentUser actor) {
        Patch.Parsed<CounterUpdate> pr = patch.parse(body, CounterUpdate.class);
        Counter c = db.lock(Counter.class, id, "Counter");
        Map<String, Object> old = m(), neu = m();
        String number = pr.dto().counterNumber();
        if (number != null && !number.equals(c.getCounterNumber())) {
            if (db.exists("select x.counterId from Counter x where x.officeId = :o and x.counterNumber = :n",
                    "o", c.getOfficeId(), "n", number)) {
                throw ApiException.conflict("DUPLICATE", "Counter number already exists in this office");
            }
            old.put("counter_number", c.getCounterNumber());
            neu.put("counter_number", number);
            c.setCounterNumber(number);
        }
        if (pr.has("service_category")) {
            old.put("service_category", c.getServiceCategory());
            neu.put("service_category", pr.dto().serviceCategory());
            c.setServiceCategory(pr.dto().serviceCategory());
        }
        if (!neu.isEmpty()) audit.record("counters", id, "UPDATE", old, neu, actor.userId());
        db.flush();
        return c;
    }

    public void deleteCounter(Long id, CurrentUser actor) {
        Counter c = db.lock(Counter.class, id, "Counter");
        audit.record("counters", id, "DELETE", m("office_id", c.getOfficeId(), "counter_number", c.getCounterNumber()), null, actor.userId());
        db.remove(c);   // appointment_slots.counter_id is ON DELETE SET NULL; history is preserved
        db.flush();
    }

    @Transactional(readOnly = true)
    public List<Counter> counters(Long officeId) {
        db.get(RtoOffice.class, officeId, "RTO office");
        return db.list(Counter.class, "select c from Counter c where c.officeId = :o order by c.counterNumber", "o", officeId);
    }

    // ---- departments / designations -----------------------------------------------------------------

    public Department createDepartment(DepartmentIn d, CurrentUser actor) {
        if (db.exists("select x.departmentId from Department x where x.departmentName = :n", "n", d.departmentName())) {
            throw ApiException.conflict("DUPLICATE", "Department already exists");
        }
        Department x = new Department();
        x.setDepartmentName(d.departmentName());
        db.save(x);
        audit.record("departments", x.getDepartmentId(), "INSERT", null, m("department_name", d.departmentName()), actor.userId());
        db.flush();
        return x;
    }

    @Transactional(readOnly = true)
    public List<Department> departments() {
        return db.list(Department.class, "select d from Department d order by d.departmentName");
    }

    public Designation createDesignation(DesignationIn d, CurrentUser actor) {
        if (db.exists("select x.designationId from Designation x where x.title = :t", "t", d.title())) {
            throw ApiException.conflict("DUPLICATE", "Designation already exists");
        }
        Designation x = new Designation();
        x.setTitle(d.title());
        x.setRankLevel(d.rankLevel());
        db.save(x);
        audit.record("designations", x.getDesignationId(), "INSERT", null, m("title", d.title(), "rank_level", d.rankLevel()), actor.userId());
        db.flush();
        return x;
    }

    public Designation updateDesignation(Long id, DesignationUpdate d, CurrentUser actor) {
        Designation x = db.lock(Designation.class, id, "Designation");
        Map<String, Object> old = m(), neu = m();
        if (d.title() != null && !d.title().equals(x.getTitle())) {
            if (db.exists("select y.designationId from Designation y where y.title = :t", "t", d.title())) {
                throw ApiException.conflict("DUPLICATE", "Designation already exists");
            }
            old.put("title", x.getTitle());
            neu.put("title", d.title());
            x.setTitle(d.title());
        }
        if (d.rankLevel() != null && !d.rankLevel().equals(x.getRankLevel())) {
            old.put("rank_level", x.getRankLevel());
            neu.put("rank_level", d.rankLevel());
            x.setRankLevel(d.rankLevel());
        }
        if (!neu.isEmpty()) audit.record("designations", id, "UPDATE", old, neu, actor.userId());
        db.flush();
        return x;
    }

    @Transactional(readOnly = true)
    public List<Designation> designations() {
        return db.list(Designation.class, "select d from Designation d order by d.rankLevel, d.title");
    }
}
