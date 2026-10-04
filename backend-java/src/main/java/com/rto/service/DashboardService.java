package com.rto.service;

import com.rto.core.Clock;
import com.rto.core.Db;
import com.rto.dto.SupportDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard numbers. Everything is computed by SQL aggregates (COUNT/SUM/GROUP BY); no table is loaded to be counted. */
@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final List<String> APPLICATION_STATUSES = List.of("SUBMITTED", "DOCS_PENDING", "UNDER_VERIFICATION", "APPOINTMENT_SCHEDULED",
            "AWAITING_PAYMENT", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED");
    private static final List<String> PENDING = APPLICATION_STATUSES.subList(0, 5);

    private final Db db;

    public DashboardService(Db db) {
        this.db = db;
    }

    private long count(String jpql, Object... kv) {
        return db.count(jpql, kv);
    }

    private static BigDecimal money(Object v) {
        return Fees.money(v == null ? BigDecimal.ZERO : (BigDecimal) v);
    }

    public DashboardSummary summary(int days) {
        LocalDate t = Clock.today(), horizon = t.plusDays(days);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        APPLICATION_STATUSES.forEach(s -> byStatus.put(s, 0L));
        for (Object[] r : db.rows("select a.currentStatus, count(a) from Application a group by a.currentStatus")) byStatus.put((String) r[0], (Long) r[1]);

        Object[] unpaid = db.rows("select count(c), coalesce(sum(c.totalAmount), 0) from Challan c where c.status in ('ISSUED','DISPUTED')").get(0);

        Map<String, MoneyCount> pay = new LinkedHashMap<>();
        List.of("PENDING", "SUCCESS", "FAILED", "REFUNDED").forEach(s -> pay.put(s, new MoneyCount(0, Fees.ZERO)));
        for (Object[] r : db.rows("select p.status, count(p), coalesce(sum(p.amount), 0) from Payment p group by p.status")) {
            pay.put((String) r[0], new MoneyCount((Long) r[1], money(r[2])));
        }
        BigDecimal refunded = money(db.first(BigDecimal.class, "select coalesce(sum(r.amount), 0) from Refund r where r.status = 'COMPLETED'"));
        BigDecimal gross = pay.get("SUCCESS").amount().add(pay.get("REFUNDED").amount());

        return new DashboardSummary(t, days,
                count("select count(c) from Citizen c"),
                count("select count(v) from Vehicle v"),
                count("select count(l) from DrivingLicence l where l.currentStatus = 'ACTIVE'"),
                byStatus,
                count("select count(a) from Application a where a.currentStatus in :st", "st", PENDING),
                byStatus.get("UNDER_VERIFICATION"),
                count("select count(a) from Appointment a join AppointmentSlot s on s.slotId = a.slotId where s.slotDate = :t and a.status in ('BOOKED','RESCHEDULED')", "t", t),
                count("select count(l) from DrivingLicence l where l.currentStatus = 'ACTIVE' and l.expiryDate between :a and :b", "a", t, "b", horizon),
                count("select count(f) from FitnessCertificate f where f.status = 'ACTIVE' and f.expiryDate between :a and :b", "a", t, "b", horizon),
                count("select count(f) from PollutionCertificate f where f.status = 'ACTIVE' and f.expiryDate between :a and :b", "a", t, "b", horizon),
                count("select count(i) from InsurancePolicy i where i.endDate between :a and :b", "a", t, "b", horizon),
                new MoneyCount((Long) unpaid[0], money(unpaid[1])),
                count("select count(r) from RoadTaxRecord r where r.status <> 'PAID' and r.dueDate < :t", "t", t),
                count("select count(p) from Permit p where p.status = 'ACTIVE' and p.expiryDate >= :t", "t", t),
                count("select count(c) from Complaint c where c.status in ('OPEN','IN_PROGRESS')"),
                count("select count(a) from Appeal a where a.status in ('FILED','UNDER_REVIEW')"),
                new PaymentTotals(pay, gross, refunded, gross.subtract(refunded)));
    }
}
