package com.rto.support;

import com.rto.core.Clock;
import com.rto.core.Db;
import com.rto.domain.*;
import com.rto.security.Passwords;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Direct-to-database test data builders. Every value is unique so tests never collide in the shared database. */
@Component
public class Fx {
    public static final String PASSWORD = "Passw0rd!123";
    private static final AtomicLong PHONE = new AtomicLong(1);

    private final Db db;
    private final TransactionTemplate tx;
    private final Passwords passwords;
    public final JdbcTemplate jdbc;
    private final Api api;

    @Autowired
    public Fx(Db db, PlatformTransactionManager tm, Passwords passwords, JdbcTemplate jdbc, Api api) {
        this.db = db;
        this.tx = new TransactionTemplate(tm);
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.api = api;
    }

    public static String u(int n) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, n).toUpperCase();
    }

    public <T> T inTx(java.util.function.Supplier<T> work) {
        return tx.execute(s -> work.get());
    }

    public <T> T save(T entity) {
        return inTx(() -> db.save(entity));
    }

    /** Re-read an entity in a fresh transaction (to observe what the API committed). */
    public <T> T get(Class<T> type, Object id) {
        return inTx(() -> {
            db.em().clear();
            return db.find(type, id);
        });
    }

    public void update(Object entity, Consumer<Object> change) {
        inTx(() -> {
            Object managed = db.em().merge(entity);
            change.accept(managed);
            return null;
        });
    }

    public long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    // ---- identity ----------------------------------------------------------------------------------------

    public Person makePerson() {
        Person p = new Person();
        p.setFirstName("Test");
        p.setLastName("P" + u(5));
        p.setDateOfBirth(LocalDate.of(1990, 5, 17));
        p.setGender("MALE");
        p.setNationalIdNumber("NID" + u(12));
        p.setPhonePrimary("9" + String.format("%09d", PHONE.getAndIncrement()));
        return save(p);
    }

    public Person person(Long personId) {
        return get(Person.class, personId);
    }

    public Person personOf(Citizen c) {
        return person(c.getPersonId());
    }

    public Person personOf(Employee e) {
        return person(e.getPersonId());
    }

    public Citizen makeCitizen() {
        return makeCitizen(makePerson());
    }

    public Citizen makeCitizen(Person person) {
        Citizen c = new Citizen();
        c.setPersonId(person.getPersonId());
        c.setCitizenCode("C" + u(10));
        c.setBlacklisted(false);
        return save(c);
    }

    // ---- organisation ------------------------------------------------------------------------------------

    public Region makeRegion() {
        Region r = new Region();
        r.setRegionName("Region " + u(4));
        r.setRegionCode("R" + u(6));
        return save(r);
    }

    public RtoOffice makeOffice() {
        return makeOffice(true);
    }

    public RtoOffice makeOffice(boolean active) {
        RtoOffice o = new RtoOffice();
        o.setRegionId(makeRegion().getRegionId());
        o.setOfficeName("Office " + u(4));
        o.setOfficeCode("O" + u(8));
        o.setAddressLine("1 Test Road");
        o.setIsActive(active);
        return save(o);
    }

    public Designation makeDesignation(int rank) {
        Designation d = new Designation();
        d.setTitle("Desig " + u(6));
        d.setRankLevel(rank);
        return save(d);
    }

    public Department makeDepartment() {
        Department d = new Department();
        d.setDepartmentName("Dept " + u(6));
        return save(d);
    }

    public Employee makeEmployee(RtoOffice office) {
        return makeEmployee(office, makePerson());
    }

    public Employee makeEmployee(RtoOffice office, Person person) {
        Employee e = new Employee();
        e.setPersonId(person.getPersonId());
        e.setEmployeeCode("E" + u(10));
        e.setDesignationId(makeDesignation(3).getDesignationId());
        e.setDateJoined(LocalDate.of(2020, 1, 1));
        e.setIsActive(true);
        save(e);
        if (office != null) {
            EmployeePosting p = new EmployeePosting();
            p.setEmployeeId(e.getEmployeeId());
            p.setOfficeId(office.getOfficeId());
            p.setDepartmentId(makeDepartment().getDepartmentId());
            p.setPostedFrom(LocalDate.of(2020, 1, 1));
            save(p);
        }
        return e;
    }

    // ---- users / roles -----------------------------------------------------------------------------------

    public Role makeRole(List<String> permissionKeys) {
        return inTx(() -> {
            Role r = new Role();
            r.setRoleName("ROLE_" + u(8));
            db.save(r);
            List<Permission> perms = db.list(Permission.class, "select p from Permission p where p.permissionKey in :k", "k", permissionKeys);
            if (perms.size() != permissionKeys.stream().distinct().count()) throw new IllegalArgumentException("unknown permission in test: " + permissionKeys);
            for (Permission p : perms) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(r.getRoleId());
                rp.setPermissionId(p.getPermissionId());
                db.save(rp);
            }
            return r;
        });
    }

    public User makeUserRaw(List<String> roleNames, List<String> permissions, Person person, boolean active, String username) {
        Person p = person != null ? person : makePerson();
        return inTx(() -> {
            User user = new User();
            user.setPersonId(p.getPersonId());
            user.setUsername(username != null ? username : "user_" + u(10));
            user.setPasswordHash(passwords.hash(PASSWORD));
            user.setIsActive(active);
            db.save(user);
            for (String name : roleNames) {
                UserRole ur = new UserRole();
                ur.setUserId(user.getUserId());
                ur.setRoleId(db.first(Role.class, "select r from Role r where r.roleName = :n", "n", name).getRoleId());
                db.save(ur);
            }
            return user;
        });
    }

    /** A user holding exactly the given permissions (through a freshly created role). */
    public User userWithPermissions(String... permissions) {
        return userWithPermissions(null, permissions);
    }

    public User userWithPermissions(Person person, String... permissions) {
        User user = makeUserRaw(List.of(), List.of(), person, true, null);
        Role role = makeRole(List.of(permissions));
        grant(user, role);
        return user;
    }

    public void grant(User user, Role role) {
        inTx(() -> {
            UserRole ur = new UserRole();
            ur.setUserId(user.getUserId());
            ur.setRoleId(role.getRoleId());
            return db.save(ur);
        });
    }

    public User userWithRoles(String... roles) {
        return makeUserRaw(List.of(roles), List.of(), null, true, null);
    }

    public User userWithRoles(Person person, String... roles) {
        return makeUserRaw(List.of(roles), List.of(), person, true, null);
    }

    public String login(User user) {
        Api.Resp r = api.post("/api/v1/auth/login", null, Api.m("username", user.getUsername(), "password", PASSWORD));
        if (r.status() != 200) throw new AssertionError("login failed: " + r);
        return r.s("access_token");
    }

    public String admin() {
        return login(userWithRoles("ADMIN"));
    }

    public String withPermissions(String... permissions) {
        return login(userWithPermissions(permissions));
    }

    public String citizenToken(Citizen citizen) {
        return login(userWithRoles(personOf(citizen), "CITIZEN"));
    }

    /** An employee posted at {@code office} with a login. */
    public record Staff(String token, Employee emp, User user) {}

    public Staff makeStaff(RtoOffice office, String... roles) {
        Employee emp = makeEmployee(office);
        User user = makeUserRaw(List.of(roles.length == 0 ? new String[]{"ADMIN"} : roles), List.of(), personOf(emp), true, null);
        return new Staff(login(user), emp, user);
    }

    public Staff makeStaffWithPermissions(RtoOffice office, String... permissions) {
        Employee emp = makeEmployee(office);
        User user = userWithPermissions(personOf(emp), permissions);
        return new Staff(login(user), emp, user);
    }

    // ---- reference rows ------------------------------------------------------------------------------------

    public ServiceType makeServiceType(String fee, int sla) {
        ServiceType s = new ServiceType();
        s.setServiceName("Svc " + u(5));
        s.setServiceCode("S" + u(10));
        s.setBaseFee(new BigDecimal(fee));
        s.setSlaDays(sla);
        return save(s);
    }

    public DocumentType makeDocumentType(Integer validityDays) {
        DocumentType d = new DocumentType();
        d.setTypeName("Doc " + u(8));
        d.setIsMandatoryDefault(true);
        d.setValidityPeriodDays(validityDays);
        return save(d);
    }

    public AppointmentSlot makeSlot(RtoOffice office, int capacity, int daysAhead, int hour) {
        AppointmentSlot s = new AppointmentSlot();
        s.setOfficeId(office.getOfficeId());
        s.setSlotDate(Clock.today().plusDays(daysAhead));
        s.setStartTime(LocalTime.of(hour, 0));
        s.setEndTime(LocalTime.of(hour, 30));
        s.setCapacity(capacity);
        s.setBookedCount(0);
        return save(s);
    }

    public LicenceClass makeLicenceClass() {
        LicenceClass c = new LicenceClass();
        c.setClassCode("C" + u(7));
        c.setDescription("class");
        return save(c);
    }

    public ViolationType makeViolationType(String fine) {
        ViolationType t = new ViolationType();
        t.setDescription("V " + u(10));
        t.setBaseFineAmount(new BigDecimal(fine));
        t.setIsCognizable(false);
        return save(t);
    }

    public PermitType makePermitType(int months) {
        PermitType t = new PermitType();
        t.setTypeName("PT " + u(8));
        t.setValidityMonths(months);
        return save(t);
    }

    public record Refs(Long manufacturerId, Long modelId, Long vehicleTypeId, Long fuelTypeId) {
        public java.util.Map<String, Object> body() {
            return Api.m("manufacturer_id", manufacturerId, "model_id", modelId, "vehicle_type_id", vehicleTypeId, "fuel_type_id", fuelTypeId);
        }
    }

    public Refs makeVehicleRefs(boolean commercial) {
        return inTx(() -> {
            VehicleManufacturer m = new VehicleManufacturer();
            m.setName("Mfr " + u(8));
            db.save(m);
            VehicleModel model = new VehicleModel();
            model.setManufacturerId(m.getManufacturerId());
            model.setModelName("Model " + u(6));
            db.save(model);
            VehicleType vt = new VehicleType();
            vt.setTypeName("VT " + u(8));
            vt.setIsCommercial(commercial);
            db.save(vt);
            FuelType ft = new FuelType();
            ft.setFuelName("Fuel " + u(8));
            db.save(ft);
            return new Refs(m.getManufacturerId(), model.getModelId(), vt.getVehicleTypeId(), ft.getFuelTypeId());
        });
    }
}
