package com.rto.service;

import com.rto.core.*;
import com.rto.domain.Address;
import com.rto.domain.Citizen;
import com.rto.domain.Person;
import com.rto.dto.IdentityDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.rto.core.Audit.m;

@Service
@Transactional
public class CitizenService {
    private final Db db;
    private final Audit audit;
    private final Common common;
    private final Patch patch;

    public CitizenService(Db db, Audit audit, Common common, Patch patch) {
        this.db = db;
        this.audit = audit;
        this.common = common;
        this.patch = patch;
    }

    public CitizenOut out(Citizen c, boolean sensitive, boolean withAddress) {
        Person p = db.get(Person.class, c.getPersonId(), "Person");
        Address current = withAddress ? db.first(Address.class, "select a from Address a where a.personId = :p and a.isCurrent = true "
                + "order by a.validFrom desc, a.addressId desc", "p", c.getPersonId()) : null;
        return new CitizenOut(c.getCitizenId(), c.getPersonId(), c.getCitizenCode(), p.getFirstName(), p.getLastName(),
                p.getDateOfBirth(), p.getGender(), p.getPhonePrimary(), p.getPhoneSecondary(), p.getEmail(),
                c.getBlacklisted(), c.getBlacklistReason(), c.getRegisteredAt(),
                sensitive ? p.getNationalIdNumber() : null, current);
    }

    @Transactional(readOnly = true)
    public Citizen get(Long id) {
        return db.get(Citizen.class, id, "Citizen");
    }

    /** Staff with the permission, or the citizen themself. */
    public static void assertCanAccess(CurrentUser user, Long citizenId, String permission) {
        if (user.has(permission) || user.isCitizen(citizenId)) return;
        throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: " + permission);
    }

    @Transactional(readOnly = true)
    public CitizenOut view(CurrentUser user, Long id) {
        assertCanAccess(user, id, "citizen.view");
        return out(get(id), user.has("citizen.view_sensitive") || user.isCitizen(id), true);
    }

    @Transactional(readOnly = true)
    public CitizenOut me(CurrentUser user) {
        if (user.citizenId() == null) throw ApiException.notFound("NOT_A_CITIZEN", "The authenticated user is not a registered citizen");
        return out(get(user.citizenId()), true, true);
    }

    Address addAddress(Long personId, AddressCreate d) {
        LocalDate from = d.validFrom() != null ? d.validFrom() : Clock.today();
        String type = d.addressType() != null ? d.addressType() : "CURRENT";
        if (d.validTo() != null && d.validTo().isBefore(from)) {
            throw ApiException.unprocessable("Request validation failed",
                    List.of(Map.of("field", "valid_to", "message", "valid_to must not be before valid_from")));
        }
        boolean current = d.validTo() == null;
        if (current) {   // history: previous current rows of the same type are closed, never overwritten or deleted
            List<Address> previous = db.lockList(Address.class, "select a from Address a where a.personId = :p "
                    + "and a.isCurrent = true and a.addressType = :t", "p", personId, "t", type);
            for (Address prev : previous) {
                if (from.isBefore(prev.getValidFrom())) {
                    throw ApiException.conflict("ADDRESS_CHRONOLOGY", "valid_from " + from + " is before the current " + type
                            + " address started (" + prev.getValidFrom() + ")");
                }
            }
            for (Address prev : previous) {
                prev.setIsCurrent(false);
                prev.setValidTo(from);
            }
        }
        Address a = new Address();
        a.setPersonId(personId);
        a.setLine1(d.line1());
        a.setLine2(d.line2());
        a.setCity(d.city());
        a.setState(d.state());
        a.setPincode(d.pincode());
        a.setAddressType(type);
        a.setIsCurrent(current);
        a.setValidFrom(from);
        a.setValidTo(d.validTo());
        return db.save(a);
    }

    public CitizenOut create(CitizenCreate d, CurrentUser actor) {
        if (db.exists("select p.personId from Person p where p.nationalIdNumber = :n", "n", d.nationalIdNumber())) {
            throw ApiException.conflict("DUPLICATE", "A person with this national ID already exists");
        }
        String code = d.citizenCode() != null ? d.citizenCode() : "CIT-" + Common.hex(5);
        if (db.exists("select c.citizenId from Citizen c where c.citizenCode = :c", "c", code)) {
            throw ApiException.conflict("DUPLICATE", "Citizen code already exists");
        }
        Person person = common.newPerson(new PersonFields(d.firstName(), d.lastName(), d.dateOfBirth(), d.gender(),
                d.nationalIdNumber(), d.phonePrimary(), d.phoneSecondary(), d.email()));
        Citizen c = new Citizen();
        c.setPersonId(person.getPersonId());
        c.setCitizenCode(code);
        c.setBlacklisted(false);
        db.save(c);
        if (d.address() != null) addAddress(person.getPersonId(), d.address());
        audit.record("citizens", c.getCitizenId(), "INSERT", null, m("citizen_code", code, "person_id", person.getPersonId()), actor.userId());
        db.flush();
        db.refresh(c);
        return out(c, actor.has("citizen.view_sensitive"), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<CitizenListItem> list(CurrentUser user, PageParams p, Boolean blacklisted, String phone,
                                              String citizenCode, String nationalId) {
        QB q = new QB("c", "Citizen c join Person p on p.personId = c.personId");
        if (p.search() != null) {
            String[] parts = p.search().split("\\s+");
            String t = QB.like(p.search());
            String cond = "(c.citizenCode like :s0 escape '\\' or p.firstName like :s0 escape '\\' "
                    + "or p.lastName like :s0 escape '\\' or p.phonePrimary like :s0 escape '\\'";
            if (parts.length == 2) {
                cond += " or (p.firstName like :s1 escape '\\' and p.lastName like :s2 escape '\\')";
                q.and(cond + ")", "s0", t, "s1", QB.like(parts[0]), "s2", QB.like(parts[1]));
            } else {
                q.and(cond + ")", "s0", t);
            }
        }
        if (phone != null) q.and("p.phonePrimary like :ph escape '\\'", "ph", QB.like(phone));
        q.eq("c.citizenCode", citizenCode);
        if (nationalId != null) {
            if (!user.has("citizen.view_sensitive")) {
                throw ApiException.forbidden("PERMISSION_DENIED", "Searching by national ID requires citizen.view_sensitive");
            }
            q.eq("p.nationalIdNumber", nationalId);
        }
        q.eq("c.blacklisted", blacklisted);
        return db.page(q, Citizen.class, p, Map.of("citizen_id", "c.citizenId", "citizen_code", "c.citizenCode",
                        "last_name", "p.lastName", "first_name", "p.firstName", "registered_at", "c.registeredAt"),
                "c.citizenId", false).map(c -> {
            Person pr = db.get(Person.class, c.getPersonId(), "Person");
            return new CitizenListItem(c.getCitizenId(), c.getCitizenCode(), pr.getFirstName(), pr.getLastName(),
                    pr.getPhonePrimary(), c.getBlacklisted(), c.getRegisteredAt());
        });
    }

    public CitizenOut update(Long id, com.fasterxml.jackson.databind.JsonNode body, CurrentUser actor) {
        Patch.Parsed<CitizenUpdate> pr = patch.parse(body, CitizenUpdate.class);
        CitizenUpdate d = pr.dto();
        if (Boolean.TRUE.equals(d.blacklisted()) && (d.blacklistReason() == null || d.blacklistReason().isBlank())) {
            throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", "blacklist_reason",
                    "message", "blacklist_reason is required when blacklisting a citizen")));
        }
        Citizen c = db.lock(Citizen.class, id, "Citizen");
        Person p = db.get(Person.class, c.getPersonId(), "Person");
        Map<String, Object> old = m(), neu = m();

        if (pr.has("national_id_number") && d.nationalIdNumber() != null) {
            if (!actor.has("citizen.view_sensitive")) {
                throw ApiException.forbidden("PERMISSION_DENIED", "Changing the national ID requires citizen.view_sensitive");
            }
            if (!d.nationalIdNumber().equals(p.getNationalIdNumber())
                    && db.exists("select x.personId from Person x where x.nationalIdNumber = :n", "n", d.nationalIdNumber())) {
                throw ApiException.conflict("DUPLICATE", "A person with this national ID already exists");
            }
            p.setNationalIdNumber(d.nationalIdNumber());
            neu.put("national_id_changed", true);   // the identifier itself is never written to the audit log
        }
        change(pr, "first_name", p.getFirstName(), d.firstName(), p::setFirstName, old, neu, false);
        change(pr, "last_name", p.getLastName(), d.lastName(), p::setLastName, old, neu, false);
        change(pr, "date_of_birth", p.getDateOfBirth(), d.dateOfBirth(), p::setDateOfBirth, old, neu, false);
        change(pr, "gender", p.getGender(), d.gender(), p::setGender, old, neu, false);
        change(pr, "phone_primary", p.getPhonePrimary(), d.phonePrimary(), p::setPhonePrimary, old, neu, false);
        change(pr, "phone_secondary", p.getPhoneSecondary(), d.phoneSecondary(), p::setPhoneSecondary, old, neu, true);
        change(pr, "email", p.getEmail(), d.email(), p::setEmail, old, neu, true);

        if (d.blacklisted() != null && !d.blacklisted().equals(c.getBlacklisted())) {
            old.put("blacklisted", c.getBlacklisted());
            neu.put("blacklisted", d.blacklisted());
            c.setBlacklisted(d.blacklisted());
            c.setBlacklistReason(c.getBlacklisted() ? d.blacklistReason() : null);
            neu.put("blacklist_reason", c.getBlacklistReason());
        } else if (pr.has("blacklist_reason") && Boolean.TRUE.equals(c.getBlacklisted())) {
            old.put("blacklist_reason", c.getBlacklistReason());
            neu.put("blacklist_reason", d.blacklistReason());
            c.setBlacklistReason(d.blacklistReason());
        }
        if (!neu.isEmpty()) audit.record("citizens", id, "UPDATE", old, neu, actor.userId());
        db.flush();
        return out(c, actor.has("citizen.view_sensitive"), true);
    }

    private static <T> void change(Patch.Parsed<?> pr, String json, T current, T value, java.util.function.Consumer<T> setter,
                                   Map<String, Object> old, Map<String, Object> neu, boolean nullable) {
        if (!pr.has(json) || (value == null && !nullable)) return;
        if (java.util.Objects.equals(current, value)) return;
        old.put(json, current);
        neu.put(json, value);
        setter.accept(value);
    }

    @Transactional(readOnly = true)
    public List<Address> addresses(CurrentUser user, Long citizenId, boolean currentOnly) {
        assertCanAccess(user, citizenId, "citizen.view");
        Citizen c = get(citizenId);
        return db.list(Address.class, "select a from Address a where a.personId = :p" + (currentOnly ? " and a.isCurrent = true" : "")
                + " order by a.validFrom desc, a.addressId desc", "p", c.getPersonId());
    }

    public Address addAddress(Long citizenId, AddressCreate d, CurrentUser actor) {
        Citizen c = db.lock(Citizen.class, citizenId, "Citizen");   // serialise concurrent address changes per citizen
        Address a = addAddress(c.getPersonId(), d);
        audit.record("addresses", a.getAddressId(), "INSERT", null, m("person_id", c.getPersonId(),
                "address_type", a.getAddressType(), "is_current", a.getIsCurrent(), "valid_from", a.getValidFrom()), actor.userId());
        db.flush();
        return a;
    }
}
