package com.rto.service;

import com.rto.core.Db;
import com.rto.domain.ServiceType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
public class Fees {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private final Db db;

    public Fees(Db db) {
        this.db = db;
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Fee payable for an application = service base_fee + every fee_structures component in force on `on`. */
    public BigDecimal applicationFee(ServiceType st, LocalDate on) {
        BigDecimal components = db.first(BigDecimal.class, "select coalesce(sum(f.amount), 0) from FeeStructure f "
                + "where f.serviceTypeId = :s and f.effectiveFrom <= :d and (f.effectiveTo is null or f.effectiveTo >= :d)",
                "s", st.getServiceTypeId(), "d", on);
        return money(st.getBaseFee().add(components == null ? BigDecimal.ZERO : components));
    }
}
