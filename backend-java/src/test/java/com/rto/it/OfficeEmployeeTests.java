package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.domain.Department;
import com.rto.domain.Designation;
import com.rto.domain.Employee;
import com.rto.domain.EmployeePosting;
import com.rto.domain.RtoOffice;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Fx;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfficeEmployeeTests extends BaseIT {

    static Map<String, Object> person(Object... over) {
        Map<String, Object> p = m("first_name", "Ravi", "last_name", "K" + Fx.u(4), "date_of_birth", "1985-01-01", "gender", "MALE",
                "national_id_number", "EID" + Fx.u(10), "phone_primary", "98" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)));
        for (int i = 0; i < over.length; i += 2) p.put((String) over[i], over[i + 1]);
        return p;
    }

    @Test
    void officeCrudAndUniqueCode() {
        String h = fx.admin();
        var region = fx.makeRegion();
        Map<String, Object> body = m("region_id", region.getRegionId(), "office_name", "Pune RTO", "office_code", "P" + Fx.u(8), "address_line", "x");
        Resp r = api.post("/api/v1/offices", h, body);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.b("is_active")).isTrue();
        assertThat(api.post("/api/v1/offices", h, body).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/offices", h, m("region_id", 99999999, "office_name", "n", "office_code", "Z" + Fx.u(8), "address_line", "a")).status()).isEqualTo(404);
        long oid = r.l("office_id");
        assertThat(api.patch("/api/v1/offices/" + oid, h, m("is_active", false)).b("is_active")).isFalse();
        Resp listed = api.get("/api/v1/offices", h, m("is_active", false, "region_id", region.getRegionId()));
        assertThat(listed.json().get("items")).hasSize(1);
        assertThat(listed.at("items/0/office_id").asLong()).isEqualTo(oid);
    }

    @Test
    void counterNumbersAreUniqueWithinAnOfficeOnly() {
        String h = fx.admin();
        RtoOffice o1 = fx.makeOffice(), o2 = fx.makeOffice();
        assertThat(api.post("/api/v1/offices/" + o1.getOfficeId() + "/counters", h, m("counter_number", "C1")).status()).isEqualTo(201);
        assertThat(api.post("/api/v1/offices/" + o1.getOfficeId() + "/counters", h, m("counter_number", "C1")).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/offices/" + o2.getOfficeId() + "/counters", h, m("counter_number", "C1")).status()).isEqualTo(201);
        JsonNode counters = api.get("/api/v1/offices/" + o1.getOfficeId() + "/counters", h).json();
        assertThat(counters).hasSize(1);
        assertThat(api.delete("/api/v1/counters/" + counters.get(0).get("counter_id").asLong(), h).status()).isEqualTo(204);
    }

    @Test
    void officeManagementRequiresPermission() {
        String low = fx.withPermissions("employee.view");
        var region = fx.makeRegion();
        assertThat(api.post("/api/v1/offices", low, m("region_id", region.getRegionId(), "office_name", "n", "office_code", "Q" + Fx.u(6), "address_line", "a")).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/offices", low).status()).isEqualTo(200);   // any authenticated user can read
    }

    private Map<String, Object> posting(RtoOffice o, Department d, String from) {
        return m("office_id", o.getOfficeId(), "department_id", d.getDepartmentId(), "posted_from", from);
    }

    @Test
    void employeeCreationDesignationAndPosting() {
        String h = fx.admin();
        RtoOffice office = fx.makeOffice();
        Department dept = fx.makeDepartment();
        Designation desig = fx.makeDesignation(2);
        Map<String, Object> body = m("person", person(), "designation_id", desig.getDesignationId(), "date_joined", "2019-04-01", "posting", posting(office, dept, "2019-04-01"));
        Resp r = api.post("/api/v1/employees", h, body);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.i("rank_level")).isEqualTo(2);
        assertThat(r.at("current_posting/is_current").asBoolean()).isTrue();
        @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) body.get("person");
        assertThat(api.post("/api/v1/employees", h, m("person", person("national_id_number", p.get("national_id_number")), "designation_id", desig.getDesignationId(),
                "date_joined", "2019-04-01")).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/employees", h, m("designation_id", desig.getDesignationId(), "date_joined", "2019-04-01")).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/employees", h, m("person_id", r.l("person_id"), "designation_id", desig.getDesignationId(), "date_joined", "2020-01-01")).status()).isEqualTo(409);
    }

    @Test
    void transferPreservesPostingHistoryAndKeepsASingleCurrentPosting() {
        String h = fx.admin();
        RtoOffice o1 = fx.makeOffice(), o2 = fx.makeOffice();
        Department dept = fx.makeDepartment();
        Resp e = api.post("/api/v1/employees", h, m("person", person(), "designation_id", fx.makeDesignation(3).getDesignationId(), "date_joined", "2018-01-01",
                "posting", posting(o1, dept, "2018-01-01")));
        long eid = e.l("employee_id");
        Resp r = api.post("/api/v1/employees/" + eid + "/postings", h, posting(o2, dept, "2021-07-01"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.b("is_current")).isTrue();
        JsonNode hist = api.get("/api/v1/employees/" + eid + "/postings", h).json();
        assertThat(hist.get(0).get("office_id").asLong()).isEqualTo(o2.getOfficeId());
        assertThat(hist.get(1).get("office_id").asLong()).isEqualTo(o1.getOfficeId());
        assertThat(hist.get(1).get("posted_to").asText()).isEqualTo("2021-07-01");
        assertThat(hist.get(0).get("posted_to").isNull()).isTrue();
        assertThat(fx.count("SELECT COUNT(*) FROM employee_postings WHERE employee_id = ? AND posted_to IS NULL", eid)).isEqualTo(1);
        Resp back = api.post("/api/v1/employees/" + eid + "/postings", h, posting(o1, dept, "2021-01-01"));
        assertThat(back.status()).isEqualTo(409);
        assertThat(back.code()).isEqualTo("POSTING_CHRONOLOGY");
        assertThat(api.get("/api/v1/employees", h, m("office_id", o2.getOfficeId())).at("items/0/employee_id").asLong()).isEqualTo(eid);
        List<Long> atO1 = new java.util.ArrayList<>();
        api.get("/api/v1/employees", h, m("office_id", o1.getOfficeId())).json().get("items").forEach(x -> atO1.add(x.get("employee_id").asLong()));
        assertThat(atO1).doesNotContain(eid);
    }

    @Test
    void databaseRejectsASecondCurrentPosting() {
        RtoOffice o = fx.makeOffice();
        Employee emp = fx.makeEmployee(o);
        EmployeePosting dup = new EmployeePosting();
        dup.setEmployeeId(emp.getEmployeeId());
        dup.setOfficeId(o.getOfficeId());
        dup.setDepartmentId(fx.makeDepartment().getDepartmentId());
        dup.setPostedFrom(LocalDate.of(2022, 1, 1));
        assertThatThrownBy(() -> fx.save(dup)).isInstanceOf(RuntimeException.class);   // uq_employee_current_posting (generated column)
    }

    @Test
    void inactiveOfficeCannotReceivePostings() {
        String h = fx.admin();
        RtoOffice inactive = fx.makeOffice(false);
        Resp r = api.post("/api/v1/employees", h, m("person", person(), "designation_id", fx.makeDesignation(3).getDesignationId(), "date_joined", "2018-01-01",
                "posting", posting(inactive, fx.makeDepartment(), "2018-01-01")));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("OFFICE_INACTIVE");
    }

    @Test
    void deactivatingAnEmployeeClosesTheirPostingButKeepsIt() {
        String h = fx.admin();
        RtoOffice o = fx.makeOffice();
        Department dept = fx.makeDepartment();
        Resp e = api.post("/api/v1/employees", h, m("person", person(), "designation_id", fx.makeDesignation(3).getDesignationId(), "date_joined", "2018-01-01",
                "posting", posting(o, dept, "2018-01-01")));
        long eid = e.l("employee_id");
        Resp r = api.patch("/api/v1/employees/" + eid, h, m("is_active", false));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.b("is_active")).isFalse();
        assertThat(r.isNull("current_posting")).isTrue();
        JsonNode hist = api.get("/api/v1/employees/" + eid + "/postings", h).json();
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).get("posted_to").isNull()).isFalse();
        assertThat(api.post("/api/v1/employees/" + eid + "/postings", h, posting(o, dept, "2030-01-01")).status()).isEqualTo(409);
    }
}
