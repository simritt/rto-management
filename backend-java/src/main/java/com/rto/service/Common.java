package com.rto.service;

import com.rto.core.ApiException;
import com.rto.core.Db;
import com.rto.domain.Employee;
import com.rto.domain.Person;
import com.rto.domain.RtoOffice;
import com.rto.dto.IdentityDto.PersonFields;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/** Helpers shared by every service (the equivalent of the reference backend's services/common.py). */
@Component
public class Common {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Db db;

    public Common(Db db) {
        this.db = db;
    }

    public RtoOffice requireActiveOffice(Long officeId) {
        RtoOffice o = db.get(RtoOffice.class, officeId, "RTO office");
        if (!Boolean.TRUE.equals(o.getIsActive())) {
            throw ApiException.conflict("OFFICE_INACTIVE", "RTO office is inactive and cannot receive new assignments");
        }
        return o;
    }

    public Employee requireActiveEmployee(Long employeeId, String label) {
        Employee e = db.get(Employee.class, employeeId, label);
        if (!Boolean.TRUE.equals(e.getIsActive())) throw ApiException.conflict("EMPLOYEE_INACTIVE", label + " is inactive");
        return e;
    }

    /** Reject anything not explicitly listed in the transition table with HTTP 409. */
    public static void checkTransition(Map<String, Set<String>> table, String current, String next, String what) {
        if (!table.getOrDefault(current, Set.of()).contains(next)) {
            Set<String> allowed = new java.util.TreeSet<>(table.getOrDefault(current, Set.of()));
            throw ApiException.conflict("INVALID_TRANSITION", "Invalid " + what + " transition " + current + " -> " + next
                    + ". Allowed from " + current + ": " + (allowed.isEmpty() ? "none (terminal state)" : String.join(", ", allowed)));
        }
    }

    /** Human-facing business number; uniqueness is still enforced by the database's UNIQUE keys. */
    public static String genNumber(String prefix) {
        return prefix + "-" + java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + hex(4);
    }

    public static String hex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    /** Existing person (person_id) or a newly created one (PersonFields); exactly one must be supplied. */
    public Person resolvePerson(Long personId, PersonFields f) {
        if ((personId == null) == (f == null)) {
            throw ApiException.badRequest("INVALID_REQUEST", "Provide exactly one of person_id or person");
        }
        if (personId != null) return db.get(Person.class, personId, "Person");
        if (db.exists("select p.personId from Person p where p.nationalIdNumber = :n", "n", f.nationalIdNumber())) {
            throw ApiException.conflict("DUPLICATE", "A person with this national ID already exists");
        }
        return newPerson(f);
    }

    public Person newPerson(PersonFields f) {
        if (f.dateOfBirth().getYear() < 1900) {
            throw ApiException.unprocessable("Request validation failed",
                    java.util.List.of(Map.of("field", "date_of_birth", "message", "date_of_birth is implausible")));
        }
        Person p = new Person();
        p.setFirstName(f.firstName());
        p.setLastName(f.lastName());
        p.setDateOfBirth(f.dateOfBirth());
        p.setGender(f.gender());
        p.setNationalIdNumber(f.nationalIdNumber());
        p.setPhonePrimary(f.phonePrimary());
        p.setPhoneSecondary(f.phoneSecondary());
        p.setEmail(f.email());
        db.save(p);
        return p;
    }
}
