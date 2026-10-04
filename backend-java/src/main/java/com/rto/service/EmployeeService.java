package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.OrgDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.rto.core.Audit.m;

@Service
@Transactional
public class EmployeeService {
    private final Db db;
    private final Audit audit;
    private final Common common;

    public EmployeeService(Db db, Audit audit, Common common) {
        this.db = db;
        this.audit = audit;
        this.common = common;
    }

    public static PostingOut postingOut(EmployeePosting p) {
        return new PostingOut(p.getPostingId(), p.getEmployeeId(), p.getOfficeId(), p.getDepartmentId(), p.getPostedFrom(),
                p.getPostedTo(), p.getPostedTo() == null);
    }

    EmployeePosting currentPosting(Long employeeId) {
        return db.first(EmployeePosting.class, "select p from EmployeePosting p where p.employeeId = :e and p.postedTo is null",
                "e", employeeId);
    }

    public EmployeeOut out(Employee e) {
        Person person = db.get(Person.class, e.getPersonId(), "Person");
        Designation d = db.get(Designation.class, e.getDesignationId(), "Designation");
        EmployeePosting cp = currentPosting(e.getEmployeeId());
        return new EmployeeOut(e.getEmployeeId(), e.getPersonId(), e.getEmployeeCode(), person.getFirstName(), person.getLastName(),
                e.getDesignationId(), d.getTitle(), d.getRankLevel(), e.getDateJoined(), e.getIsActive(),
                cp == null ? null : postingOut(cp));
    }

    /** Closes the open posting (if any) and opens a new one. History rows are never deleted. */
    private EmployeePosting insertPosting(Employee emp, PostingCreate d) {
        common.requireActiveOffice(d.officeId());
        db.get(Department.class, d.departmentId(), "Department");
        EmployeePosting cur = currentPosting(emp.getEmployeeId());
        if (cur != null) {
            if (!d.postedFrom().isAfter(cur.getPostedFrom())) {
                throw ApiException.conflict("POSTING_CHRONOLOGY", "New posting must start after the current posting began (" + cur.getPostedFrom() + ")");
            }
            cur.setPostedTo(d.postedFrom());
            db.flush();   // the old row must be closed before the new open row is inserted (uq_employee_current_posting)
        } else {
            LocalDate lastEnd = db.first(LocalDate.class, "select max(p.postedTo) from EmployeePosting p where p.employeeId = :e", "e", emp.getEmployeeId());
            if (lastEnd != null && d.postedFrom().isBefore(lastEnd)) {
                throw ApiException.conflict("POSTING_CHRONOLOGY", "New posting overlaps a previous posting ending " + lastEnd);
            }
        }
        EmployeePosting p = new EmployeePosting();
        p.setEmployeeId(emp.getEmployeeId());
        p.setOfficeId(d.officeId());
        p.setDepartmentId(d.departmentId());
        p.setPostedFrom(d.postedFrom());
        return db.save(p);
    }

    public EmployeeOut create(EmployeeCreate d, CurrentUser actor) {
        if ((d.personId() == null) == (d.person() == null)) throw ApiException.badRequest("INVALID_REQUEST", "Provide exactly one of person_id or person");
        db.get(Designation.class, d.designationId(), "Designation");
        Person person;
        if (d.personId() != null) {
            person = db.get(Person.class, d.personId(), "Person");
            if (db.exists("select e.employeeId from Employee e where e.personId = :p", "p", person.getPersonId())) {
                throw ApiException.conflict("DUPLICATE", "This person is already an employee");
            }
        } else {
            person = common.resolvePerson(null, d.person());
        }
        String code = d.employeeCode() != null ? d.employeeCode() : "EMP-" + Common.hex(5);
        if (db.exists("select e.employeeId from Employee e where e.employeeCode = :c", "c", code)) {
            throw ApiException.conflict("DUPLICATE", "Employee code already exists");
        }
        Employee emp = new Employee();
        emp.setPersonId(person.getPersonId());
        emp.setEmployeeCode(code);
        emp.setDesignationId(d.designationId());
        emp.setDateJoined(d.dateJoined());
        emp.setIsActive(true);
        db.save(emp);
        if (d.posting() != null) insertPosting(emp, d.posting());
        audit.record("employees", emp.getEmployeeId(), "INSERT", null,
                m("employee_code", code, "person_id", person.getPersonId(), "designation_id", d.designationId()), actor.userId());
        db.flush();
        return out(emp);
    }

    @Transactional(readOnly = true)
    public Employee get(Long id) {
        return db.get(Employee.class, id, "Employee");
    }

    @Transactional(readOnly = true)
    public EmployeeOut view(Long id) {
        return out(get(id));
    }

    public EmployeeOut update(Long id, EmployeeUpdate d, CurrentUser actor) {
        Employee emp = db.lock(Employee.class, id, "Employee");
        Map<String, Object> old = m(), neu = m();
        if (d.designationId() != null && !d.designationId().equals(emp.getDesignationId())) {
            db.get(Designation.class, d.designationId(), "Designation");
            old.put("designation_id", emp.getDesignationId());
            neu.put("designation_id", d.designationId());
            emp.setDesignationId(d.designationId());
        }
        if (d.isActive() != null && !d.isActive().equals(emp.getIsActive())) {
            old.put("is_active", emp.getIsActive());
            neu.put("is_active", d.isActive());
            emp.setIsActive(d.isActive());
            if (!d.isActive()) {   // leaving service: end the open posting, keep the row as history
                EmployeePosting cur = currentPosting(id);
                if (cur != null) {
                    LocalDate today = Clock.today();
                    cur.setPostedTo(today.isBefore(cur.getPostedFrom()) ? cur.getPostedFrom() : today);
                    neu.put("posting_closed", cur.getPostingId());
                }
            }
        }
        if (!neu.isEmpty()) audit.record("employees", id, "UPDATE", old, neu, actor.userId());
        db.flush();
        return out(emp);
    }

    public PostingOut addPosting(Long employeeId, PostingCreate d, CurrentUser actor) {
        Employee emp = db.lock(Employee.class, employeeId, "Employee");   // serialises concurrent transfers
        if (!Boolean.TRUE.equals(emp.getIsActive())) throw ApiException.conflict("EMPLOYEE_INACTIVE", "Inactive employees cannot be posted");
        EmployeePosting p = insertPosting(emp, d);
        audit.record("employee_postings", p.getPostingId(), "INSERT", null,
                m("employee_id", employeeId, "office_id", d.officeId(), "posted_from", d.postedFrom()), actor.userId());
        db.flush();
        return postingOut(p);
    }

    @Transactional(readOnly = true)
    public List<PostingOut> postings(Long employeeId) {
        get(employeeId);
        return db.list(EmployeePosting.class, "select p from EmployeePosting p where p.employeeId = :e "
                + "order by p.postedFrom desc, p.postingId desc", "e", employeeId).stream().map(EmployeeService::postingOut).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeOut> list(PageParams p, Long officeId, Long designationId, Boolean isActive) {
        QB q = new QB("e", "Employee e join Person pr on pr.personId = e.personId")
                .search(p.search(), "e.employeeCode", "pr.firstName", "pr.lastName");
        if (officeId != null) {
            q.and("e.employeeId in (select ep.employeeId from EmployeePosting ep where ep.officeId = :off and ep.postedTo is null)", "off", officeId);
        }
        q.eq("e.designationId", designationId).eq("e.isActive", isActive);
        return db.page(q, Employee.class, p, Map.of("employee_code", "e.employeeCode", "last_name", "pr.lastName",
                "date_joined", "e.dateJoined", "employee_id", "e.employeeId"), "e.employeeId", false).map(this::out);
    }
}
