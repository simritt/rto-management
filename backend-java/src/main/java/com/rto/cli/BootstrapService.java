package com.rto.cli;

import com.rto.core.Db;
import com.rto.core.Perms;
import com.rto.core.Settings;
import com.rto.domain.*;
import com.rto.security.Passwords;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Idempotent seeding of data the application cannot run without (RBAC catalogue, payable types) + the first admin. */
@Service
@Transactional
public class BootstrapService {
    private final Db db;
    private final Passwords passwords;
    private final Settings settings;

    public BootstrapService(Db db, Passwords passwords, Settings settings) {
        this.db = db;
        this.passwords = passwords;
        this.settings = settings;
    }

    public void seedRbac() {
        Map<String, Permission> perms = new HashMap<>();
        db.list(Permission.class, "select p from Permission p").forEach(p -> perms.put(p.getPermissionKey(), p));
        for (String key : Perms.ALL) {
            if (!perms.containsKey(key)) {
                Permission p = new Permission();
                p.setPermissionKey(key);
                perms.put(key, db.save(p));
            }
        }
        Map<String, Role> roles = new HashMap<>();
        db.list(Role.class, "select r from Role r").forEach(r -> roles.put(r.getRoleName(), r));
        for (String name : Perms.ROLE_GRANTS.keySet()) {
            if (!roles.containsKey(name)) {
                Role r = new Role();
                r.setRoleName(name);
                roles.put(name, db.save(r));
            }
        }
        db.flush();
        Set<String> have = new HashSet<>();
        db.list(RolePermission.class, "select rp from RolePermission rp").forEach(rp -> have.add(rp.getRoleId() + ":" + rp.getPermissionId()));
        Perms.ROLE_GRANTS.forEach((role, keys) -> keys.forEach(key -> {
            Long roleId = roles.get(role).getRoleId(), permId = perms.get(key).getPermissionId();
            if (have.add(roleId + ":" + permId)) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permId);
                db.save(rp);
            }
        }));
    }

    public void seedPayableTypes() {
        Set<String> have = new HashSet<>(db.list(String.class, "select t.typeName from PayableType t"));
        for (String name : Perms.PAYABLE_TYPES) {
            if (have.add(name)) {
                PayableType t = new PayableType();
                t.setTypeName(name);
                db.save(t);
            }
        }
    }

    private <T> void ensure(Class<T> type, String keyProp, String key, java.util.function.Supplier<T> make) {
        if (!db.exists("select e." + keyProp + " from " + type.getSimpleName() + " e where e." + keyProp + " = :k", "k", key)) db.save(make.get());
    }

    /** Optional starter master data (--sample-data). Safe to re-run. */
    public void seedSample() {
        for (String[] c : new String[][]{{"MC50CC", "Motorcycle without gear (up to 50cc)"}, {"MCWG", "Motorcycle with gear"},
                {"LMV", "Light Motor Vehicle"}, {"HMV", "Heavy Motor Vehicle"}, {"TRANS", "Transport vehicle"}}) {
            ensure(LicenceClass.class, "classCode", c[0], () -> {
                LicenceClass x = new LicenceClass();
                x.setClassCode(c[0]);
                x.setDescription(c[1]);
                return x;
            });
        }
        for (String f : List.of("Petrol", "Diesel", "CNG", "Electric", "Hybrid")) {
            ensure(FuelType.class, "fuelName", f, () -> {
                FuelType x = new FuelType();
                x.setFuelName(f);
                return x;
            });
        }
        for (Object[] v : new Object[][]{{"Two-Wheeler", false}, {"LMV", false}, {"HMV", true}, {"Transport", true}}) {
            ensure(VehicleType.class, "typeName", (String) v[0], () -> {
                VehicleType x = new VehicleType();
                x.setTypeName((String) v[0]);
                x.setIsCommercial((Boolean) v[1]);
                return x;
            });
        }
        for (Object[] v : new Object[][]{{"State", 60}, {"National", 12}, {"Route", 60}, {"Temporary", 4}}) {
            ensure(PermitType.class, "typeName", (String) v[0], () -> {
                PermitType x = new PermitType();
                x.setTypeName((String) v[0]);
                x.setValidityMonths((Integer) v[1]);
                return x;
            });
        }
        for (Object[] v : new Object[][]{{"Proof of Identity", true, null}, {"Proof of Address", true, null}, {"Medical Certificate", false, 180}}) {
            ensure(DocumentType.class, "typeName", (String) v[0], () -> {
                DocumentType x = new DocumentType();
                x.setTypeName((String) v[0]);
                x.setIsMandatoryDefault((Boolean) v[1]);
                x.setValidityPeriodDays((Integer) v[2]);
                return x;
            });
        }
        for (Object[] v : new Object[][]{{"New Learner Licence", "LL_NEW", "200.00", 3}, {"New Driving Licence", "DL_NEW", "500.00", 7},
                {"Vehicle Registration", "VEH_REG", "600.00", 7}, {"Ownership Transfer", "VEH_TRANSFER", "300.00", 10}, {"Permit Issue", "PERMIT_NEW", "1000.00", 14}}) {
            ensure(ServiceType.class, "serviceCode", (String) v[1], () -> {
                ServiceType x = new ServiceType();
                x.setServiceName((String) v[0]);
                x.setServiceCode((String) v[1]);
                x.setBaseFee(new BigDecimal((String) v[2]));
                x.setSlaDays((Integer) v[3]);
                return x;
            });
        }
        for (Object[] v : new Object[][]{{"Over-speeding", "1000.00", false}, {"Driving without licence", "5000.00", true},
                {"Signal jumping", "500.00", false}, {"No helmet", "500.00", false}}) {
            ensure(ViolationType.class, "description", (String) v[0], () -> {
                ViolationType x = new ViolationType();
                x.setDescription((String) v[0]);
                x.setBaseFineAmount(new BigDecimal((String) v[1]));
                x.setIsCognizable((Boolean) v[2]);
                return x;
            });
        }
    }

    /** @return a message describing what happened */
    public String createAdmin() {
        if (settings.bootstrapAdminPassword().isBlank()) {
            return "RBAC + payable types seeded. Set BOOTSTRAP_ADMIN_PASSWORD to create the admin user.";
        }
        String username = settings.bootstrapAdminUsername();
        if (db.exists("select u.userId from User u where u.username = :n", "n", username)) {
            return "Admin user '" + username + "' already exists; left unchanged.";
        }
        Person person = new Person();
        person.setFirstName("System");
        person.setLastName("Administrator");
        person.setDateOfBirth(LocalDate.of(1990, 1, 1));
        person.setGender("OTHER");
        person.setNationalIdNumber("SYSTEM-ADMIN");
        person.setPhonePrimary("0000000000");
        db.save(person);
        User user = new User();
        user.setPersonId(person.getPersonId());
        user.setUsername(username);
        user.setPasswordHash(passwords.hash(settings.bootstrapAdminPassword()));
        user.setIsActive(true);
        db.save(user);
        Role admin = db.first(Role.class, "select r from Role r where r.roleName = 'ADMIN'");
        UserRole ur = new UserRole();
        ur.setUserId(user.getUserId());
        ur.setRoleId(admin.getRoleId());
        db.save(ur);
        return "Created admin user '" + username + "'.";
    }
}
