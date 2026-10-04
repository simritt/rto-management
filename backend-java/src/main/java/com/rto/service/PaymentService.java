package com.rto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.PaymentDto.*;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.rto.core.Audit.m;

/**
 * Polymorphic payment engine. payments.payable_id has NO database foreign key, so every operation resolves
 * (payable_type, payable_id) against the real table, checks the target is payable and that the amount matches.
 * Lock order is ALWAYS target row -> payment row (create, attempt, refund) so concurrent requests serialise
 * instead of deadlocking. Money is BigDecimal end to end.
 */
@Service
@Transactional
public class PaymentService {
    private record Target(Object entity, BigDecimal due) {}

    private record Locked(Payment payment, String type, Object target) {}

    private final Db db;
    private final Audit audit;
    private final Fees fees;
    private final ViolationService violations;
    private final Notifications notifications;
    private final Patch patch;

    public PaymentService(Db db, Audit audit, Fees fees, ViolationService violations, Notifications notifications, Patch patch) {
        this.db = db;
        this.audit = audit;
        this.fees = fees;
        this.violations = violations;
        this.notifications = notifications;
        this.patch = patch;
    }

    // ---- target resolution -----------------------------------------------------------------------------------

    private PayableType payableType(String name) {
        PayableType pt = db.first(PayableType.class, "select t from PayableType t where t.typeName = :n", "n", name);
        if (pt == null) throw ApiException.conflict("PAYABLE_TYPE_MISSING", "Payable type " + name + " is not configured; run the bootstrap command");
        return pt;
    }

    private static Class<?> classOf(String type) {
        return switch (type) {
            case "APPLICATION" -> Application.class;
            case "CHALLAN" -> Challan.class;
            case "PERMIT" -> Permit.class;
            default -> RoadTaxRecord.class;
        };
    }

    private Object lockTarget(String type, Long id) {
        Class<?> cls = classOf(type);
        if (db.find(cls, id) == null) throw ApiException.notFound("PAYABLE_NOT_FOUND", type + " " + id + " does not exist");
        return db.lock(cls, id, type);
    }

    private Target resolveTarget(String type, Long id) {
        Object target = lockTarget(type, id);
        BigDecimal due = null;
        switch (type) {
            case "APPLICATION" -> {
                Application a = (Application) target;
                if (!"AWAITING_PAYMENT".equals(a.getCurrentStatus())) {
                    throw ApiException.conflict("NOT_PAYABLE", "The application is " + a.getCurrentStatus() + "; only AWAITING_PAYMENT applications are payable");
                }
                due = fees.applicationFee(db.get(ServiceType.class, a.getServiceTypeId(), "Service type"), a.getSubmittedAt().toLocalDate());
                if (due.signum() <= 0) throw ApiException.conflict("NOT_PAYABLE", "No fee is due for this application");
            }
            case "CHALLAN" -> {
                Challan c = (Challan) target;
                if (!"ISSUED".equals(c.getStatus())) throw ApiException.conflict("NOT_PAYABLE", "The challan is " + c.getStatus() + "; only ISSUED challans are payable");
                due = Fees.money(c.getTotalAmount());
            }
            case "ROAD_TAX" -> {
                RoadTaxRecord r = (RoadTaxRecord) target;
                if (!List.of("DUE", "OVERDUE").contains(r.getStatus())) throw ApiException.conflict("NOT_PAYABLE", "The road-tax record is " + r.getStatus());
                due = Fees.money(r.getAmountDue());
            }
            default -> {   // PERMIT: no fee schedule exists for permits, the cashier supplies the amount
                Permit p = (Permit) target;
                if (!List.of("ACTIVE", "SUSPENDED").contains(p.getStatus())) throw ApiException.conflict("NOT_PAYABLE", "The permit is " + p.getStatus());
            }
        }
        return new Target(target, due);
    }

    private Long payerPerson(String type, Object target) {
        switch (type) {
            case "APPLICATION" -> {
                Application a = (Application) target;
                return db.get(Citizen.class, db.get(Applicant.class, a.getApplicantId(), "Applicant").getCitizenId(), "Citizen").getPersonId();
            }
            case "PERMIT" -> {
                return db.get(Citizen.class, ((Permit) target).getCitizenId(), "Citizen").getPersonId();
            }
            default -> {
                Long citizenId = null, vehicleId;
                if (type.equals("ROAD_TAX")) {
                    vehicleId = ((RoadTaxRecord) target).getVehicleId();
                } else {
                    List<Object[]> rows = db.rows("select v.driverCitizenId, v.vehicleId from Violation v join ChallanViolation cv on cv.violationId = v.violationId "
                            + "where cv.challanId = :c", "c", ((Challan) target).getChallanId());
                    if (rows.isEmpty()) return null;
                    citizenId = (Long) rows.get(0)[0];
                    vehicleId = (Long) rows.get(0)[1];
                }
                if (citizenId == null) {
                    citizenId = db.first(Long.class, "select o.citizenId from VehicleOwnership o where o.vehicleId = :v and o.effectiveTo is null", "v", vehicleId);
                }
                return citizenId == null ? null : db.get(Citizen.class, citizenId, "Citizen").getPersonId();
            }
        }
    }

    // ---- representation ---------------------------------------------------------------------------------------

    public BigDecimal refunded(Long paymentId) {
        BigDecimal t = db.first(BigDecimal.class, "select coalesce(sum(r.amount), 0) from Refund r where r.paymentId = :p and r.status in ('INITIATED','COMPLETED')", "p", paymentId);
        return Fees.money(t == null ? BigDecimal.ZERO : t);
    }

    public PaymentOut out(Payment p, boolean replay) {
        BigDecimal refunded = refunded(p.getPaymentId());
        BigDecimal refundable = "SUCCESS".equals(p.getStatus()) ? Fees.money(p.getAmount().subtract(refunded)) : Fees.ZERO;
        return new PaymentOut(p.getPaymentId(), p.getReceiptNumber(), db.get(PayableType.class, p.getPayableTypeId(), "Payable type").getTypeName(),
                p.getPayableId(), p.getAmount(), p.getStatus(), p.getPaidAt(), p.getCreatedAt(), refunded, refundable,
                db.count("select count(a) from PaymentAttempt a where a.paymentId = :p", "p", p.getPaymentId()), replay);
    }

    // ---- creation ---------------------------------------------------------------------------------------------

    private List<Payment> existing(PayableType pt, Long payableId) {
        return db.list(Payment.class, "select p from Payment p where p.payableTypeId = :t and p.payableId = :i order by p.paymentId", "t", pt.getPayableTypeId(), "i", payableId);
    }

    private Payment newPayment(PayableType pt, Long payableId, BigDecimal amount) {
        Payment p = new Payment();
        p.setReceiptNumber(Common.genNumber("RCT"));
        p.setPayableTypeId(pt.getPayableTypeId());
        p.setPayableId(payableId);
        p.setAmount(amount);
        p.setStatus("PENDING");
        p.setCreatedAt(Clock.now());
        return db.save(p);
    }

    private static BigDecimal validatedAmount(BigDecimal due, BigDecimal given) {
        if (due == null) {
            if (given == null) throw ApiException.badRequest("AMOUNT_REQUIRED", "amount is required for this payable type");
            return Fees.money(given);
        }
        if (given != null && Fees.money(given).compareTo(due) != 0) {
            throw ApiException.conflict("AMOUNT_MISMATCH", "The amount due is " + due.toPlainString() + "; " + given.toPlainString() + " was submitted");
        }
        return due;
    }

    /** Returns the payment a previous request with this gateway reference produced (idempotent replay), or null. */
    private Payment replayOf(String reference, PayableType pt, Long payableId) {
        PaymentAttempt prior = db.first(PaymentAttempt.class, "select a from PaymentAttempt a where a.gatewayReference = :g", "g", reference);
        if (prior == null) return null;
        Payment pp = db.get(Payment.class, prior.getPaymentId(), "Payment");
        if (pp.getPayableTypeId().equals(pt.getPayableTypeId()) && pp.getPayableId().equals(payableId)) return pp;
        throw ApiException.conflict("GATEWAY_REFERENCE_REUSED", "This gateway reference was already used for a different payment");
    }

    public PaymentOut create(PaymentCreate d, CurrentUser user) {
        if (d.outcome() != null && d.gatewayReference() == null) {
            throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", "gateway_reference", "message", "gateway_reference is required when an outcome is supplied")));
        }
        PayableType pt = payableType(d.payableType());
        if (d.gatewayReference() != null) {   // a re-sent request carrying the same gateway reference returns the original result
            Payment replay = replayOf(d.gatewayReference(), pt, d.payableId());
            if (replay != null) return out(replay, true);
        }
        Target target = resolveTarget(d.payableType(), d.payableId());   // locks the target row first
        List<Payment> existing = existing(pt, d.payableId());
        if (existing.stream().anyMatch(p -> p.getStatus().equals("SUCCESS"))) throw ApiException.conflict("ALREADY_PAID", "This item has already been paid");
        Payment pending = existing.stream().filter(p -> p.getStatus().equals("PENDING")).findFirst().orElse(null);
        if (pending != null) {
            throw ApiException.conflict("PAYMENT_PENDING", "Payment " + pending.getPaymentId() + " is already pending for this item; record attempts on it");
        }
        BigDecimal amount = validatedAmount(target.due(), d.amount());
        Payment p = newPayment(pt, d.payableId(), amount);
        audit.record("payments", p.getPaymentId(), "INSERT", null, m("payable_type", d.payableType(), "payable_id", d.payableId(),
                "amount", amount, "receipt_number", p.getReceiptNumber()), user.userId());
        boolean replay = false;
        if (d.gatewayReference() != null && d.outcome() != null) replay = recordAttempt(p, d.gatewayReference(), d.outcome(), user, d.payableType(), target.entity());
        db.flush();
        notifyOutcome(p, d.payableType(), target.entity());
        return out(p, replay);
    }

    // ---- attempts -----------------------------------------------------------------------------------------------

    /** target -> payment lock order, the same as create. */
    private Locked lockForAttempt(Long paymentId) {
        Payment probe = db.get(Payment.class, paymentId, "Payment");
        String type = db.get(PayableType.class, probe.getPayableTypeId(), "Payable type").getTypeName();
        if (db.find(classOf(type), probe.getPayableId()) == null) {
            throw ApiException.notFound("PAYABLE_NOT_FOUND", "The " + type + " this payment was made for no longer exists");
        }
        Object target = db.lock(classOf(type), probe.getPayableId(), type);
        Payment p = db.lock(Payment.class, paymentId, "Payment");
        return new Locked(p, type, target);
    }

    /** Appends an attempt and settles the payment. Returns true when this was an idempotent replay. */
    private boolean recordAttempt(Payment p, String reference, String outcome, CurrentUser user, String type, Object target) {
        PaymentAttempt prior = db.first(PaymentAttempt.class, "select a from PaymentAttempt a where a.gatewayReference = :g", "g", reference);
        if (prior != null) {
            if (!prior.getPaymentId().equals(p.getPaymentId())) {
                throw ApiException.conflict("GATEWAY_REFERENCE_REUSED", "This gateway reference was already used for a different payment");
            }
            return true;   // same payment, same reference: replay, nothing is written twice
        }
        if ("SUCCESS".equals(p.getStatus())) throw ApiException.conflict("PAYMENT_ALREADY_SUCCESSFUL", "This payment has already succeeded");
        if ("REFUNDED".equals(p.getStatus())) throw ApiException.conflict("PAYMENT_REFUNDED", "This payment has been refunded");
        if (db.exists("select x.paymentId from Payment x where x.payableTypeId = :t and x.payableId = :i and x.status = 'SUCCESS' and x.paymentId <> :self",
                "t", p.getPayableTypeId(), "i", p.getPayableId(), "self", p.getPaymentId())) {
            throw ApiException.conflict("ALREADY_PAID", "This item has already been paid by another payment");
        }
        Integer max = db.first(Integer.class, "select max(a.attemptNumber) from PaymentAttempt a where a.paymentId = :p", "p", p.getPaymentId());
        int n = (max == null ? 0 : max) + 1;
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(p.getPaymentId());
        attempt.setAttemptNumber(n);
        attempt.setGatewayReference(reference);
        attempt.setOutcome(outcome);
        attempt.setAttemptedAt(Clock.now());
        db.save(attempt);
        String old = p.getStatus();
        if (outcome.equals("SUCCESS")) {
            p.setStatus("SUCCESS");
            p.setPaidAt(Clock.now());
            settleTarget(type, target, user.userId());
        } else if (outcome.equals("FAILED")) {
            p.setStatus("FAILED");
        }
        // TIMEOUT: the gateway result is unknown, so the payment stays PENDING and can be retried/reconciled
        db.flush();
        audit.record("payment_attempts", attempt.getAttemptId(), "INSERT", null, m("payment_id", p.getPaymentId(), "attempt_number", n, "outcome", outcome), user.userId());
        if (!p.getStatus().equals(old)) audit.record("payments", p.getPaymentId(), "UPDATE", m("status", old), m("status", p.getStatus()), user.userId());
        return false;
    }

    private void settleTarget(String type, Object target, Long userId) {
        if (type.equals("CHALLAN")) {
            violations.applyStatus((Challan) target, "PAID", userId, "Fine paid");
        } else if (type.equals("ROAD_TAX")) {
            RoadTaxRecord r = (RoadTaxRecord) target;
            String old = r.getStatus();
            r.setStatus("PAID");
            audit.record("road_tax_records", r.getTaxRecordId(), "UPDATE", m("status", old), m("status", "PAID"), userId);
        }
        // APPLICATION: workflow approval re-checks for a SUCCESS payment; PERMIT: nothing to flip
    }

    public PaymentOut retry(Long paymentId, String reference, String outcome, CurrentUser user) {
        Locked l = lockForAttempt(paymentId);
        boolean replay = recordAttempt(l.payment(), reference, outcome, user, l.type(), l.target());
        db.flush();
        if (!replay) notifyOutcome(l.payment(), l.type(), l.target());
        return out(l.payment(), replay);
    }

    /** Convenience used by POST /challans/{id}/pay: reuse the open payment for the target or create one. */
    public PaymentOut payTarget(String type, Long payableId, String reference, String outcome, CurrentUser user) {
        PayableType pt = payableType(type);
        // Idempotent replay is answered BEFORE target validation: the item may legitimately be PAID by now.
        Payment replay = replayOf(reference, pt, payableId);
        if (replay != null) return out(replay, true);
        Target target = resolveTarget(type, payableId);
        List<Payment> existing = existing(pt, payableId);
        if (existing.stream().anyMatch(p -> p.getStatus().equals("SUCCESS"))) throw ApiException.conflict("ALREADY_PAID", "This item has already been paid");
        Payment open = null;
        for (int i = existing.size() - 1; i >= 0 && open == null; i--) {
            if (List.of("PENDING", "FAILED").contains(existing.get(i).getStatus())) open = existing.get(i);
        }
        if (open == null) {
            open = newPayment(pt, payableId, validatedAmount(target.due(), null));
            audit.record("payments", open.getPaymentId(), "INSERT", null, m("payable_type", type, "payable_id", payableId, "amount", open.getAmount()), user.userId());
        }
        boolean rep = recordAttempt(open, reference, outcome, user, type, target.entity());
        db.flush();
        if (!rep) notifyOutcome(open, type, target.entity());
        return out(open, rep);
    }

    private void notifyOutcome(Payment p, String type, Object target) {
        if (!List.of("SUCCESS", "FAILED").contains(p.getStatus())) return;
        Long person = payerPerson(type, target);
        if (person == null) return;
        if (p.getStatus().equals("SUCCESS")) {
            notifications.notify(person, "Payment successful", "Payment of " + p.getAmount().toPlainString() + " received. Receipt " + p.getReceiptNumber() + ".");
        } else {
            notifications.notify(person, "Payment failed", "Your payment " + p.getReceiptNumber() + " could not be completed. Please retry.");
        }
    }

    // ---- refunds -------------------------------------------------------------------------------------------------

    public Refund createRefund(Long paymentId, BigDecimal amountIn, String reason, boolean autoComplete, CurrentUser user) {
        Locked l = lockForAttempt(paymentId);   // payment is locked: refunds serialise per payment
        Payment p = l.payment();
        if (!"SUCCESS".equals(p.getStatus())) throw ApiException.conflict("NOT_REFUNDABLE", "Only SUCCESS payments can be refunded (this one is " + p.getStatus() + ")");
        if (db.exists("select r.refundId from Refund r where r.paymentId = :p and r.status = 'INITIATED'", "p", paymentId)) {
            throw ApiException.conflict("REFUND_IN_PROGRESS", "A refund for this payment is already in progress");
        }
        BigDecimal amount = Fees.money(amountIn);
        BigDecimal refundable = p.getAmount().subtract(refunded(paymentId));
        if (amount.compareTo(refundable) > 0) {
            throw ApiException.conflict("REFUND_EXCEEDS_PAYMENT", "Refund of " + amount.toPlainString() + " exceeds the refundable amount " + Fees.money(refundable).toPlainString());
        }
        Refund r = new Refund();
        r.setPaymentId(paymentId);
        r.setAmount(amount);
        r.setReason(reason);
        r.setStatus("INITIATED");
        db.save(r);
        audit.record("refunds", r.getRefundId(), "INSERT", null, m("payment_id", paymentId, "amount", amount, "reason", reason), user.userId());
        if (autoComplete) complete(r, p, l.type(), l.target(), user);
        db.flush();
        return r;
    }

    private void complete(Refund r, Payment p, String type, Object target, CurrentUser user) {
        r.setStatus("COMPLETED");
        r.setProcessedAt(Clock.now());
        db.flush();
        BigDecimal completed = db.first(BigDecimal.class, "select coalesce(sum(x.amount), 0) from Refund x where x.paymentId = :p and x.status = 'COMPLETED'", "p", p.getPaymentId());
        audit.record("refunds", r.getRefundId(), "UPDATE", m("status", "INITIATED"), m("status", "COMPLETED"), user.userId());
        if (completed.compareTo(p.getAmount()) >= 0) {
            p.setStatus("REFUNDED");
            audit.record("payments", p.getPaymentId(), "UPDATE", m("status", "SUCCESS"), m("status", "REFUNDED"), user.userId());
            if (type.equals("ROAD_TAX") && "PAID".equals(((RoadTaxRecord) target).getStatus())) {   // the tax is owed again
                RoadTaxRecord t = (RoadTaxRecord) target;
                t.setStatus(t.getDueDate().isBefore(Clock.today()) ? "OVERDUE" : "DUE");
                audit.record("road_tax_records", t.getTaxRecordId(), "UPDATE", m("status", "PAID"), m("status", t.getStatus()), user.userId());
            }
        }
    }

    public Refund completeRefund(Long refundId, CurrentUser user) {
        Refund probe = db.get(Refund.class, refundId, "Refund");
        Locked l = lockForAttempt(probe.getPaymentId());
        Refund r = db.lock(Refund.class, refundId, "Refund");
        if (!"INITIATED".equals(r.getStatus())) throw ApiException.conflict("INVALID_TRANSITION", "Only INITIATED refunds can be completed (this one is " + r.getStatus() + ")");
        complete(r, l.payment(), l.type(), l.target(), user);
        db.flush();
        return r;
    }

    public Refund failRefund(Long refundId, String reason, CurrentUser user) {
        Refund probe = db.get(Refund.class, refundId, "Refund");
        lockForAttempt(probe.getPaymentId());
        Refund r = db.lock(Refund.class, refundId, "Refund");
        if (!"INITIATED".equals(r.getStatus())) throw ApiException.conflict("INVALID_TRANSITION", "Only INITIATED refunds can fail (this one is " + r.getStatus() + ")");
        r.setStatus("FAILED");   // releases the reserved amount
        r.setProcessedAt(Clock.now());
        audit.record("refunds", refundId, "UPDATE", m("status", "INITIATED"), m("status", "FAILED", "reason", reason), user.userId());
        db.flush();
        return r;
    }

    // ---- queries --------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PaymentOut view(Long id) {
        return out(db.get(Payment.class, id, "Payment"), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentOut> list(PageParams p, String status, String payableType, Long payableId, LocalDate from, LocalDate to) {
        QB q = new QB("p", "Payment p join PayableType t on t.payableTypeId = p.payableTypeId").select("p")
                .search(p.search(), "p.receiptNumber").eq("p.status", status).eq("t.typeName", payableType).eq("p.payableId", payableId);
        if (from != null) q.op("p.createdAt", ">=", from.atStartOfDay());
        if (to != null) q.op("p.createdAt", "<", to.plusDays(1).atStartOfDay());
        return db.page(q, Payment.class, p, Map.of("created_at", "p.createdAt", "amount", "p.amount", "paid_at", "p.paidAt", "payment_id", "p.paymentId"),
                "p.paymentId", true).map(x -> out(x, false));
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> attempts(Long paymentId) {
        db.get(Payment.class, paymentId, "Payment");
        return db.list(PaymentAttempt.class, "select a from PaymentAttempt a where a.paymentId = :p order by a.attemptNumber", "p", paymentId);
    }

    @Transactional(readOnly = true)
    public PageResponse<Refund> refunds(PageParams p, Long paymentId, String status) {
        QB q = new QB("r", "Refund r").eq("r.paymentId", paymentId).eq("r.status", status);
        return db.page(q, Refund.class, p, Map.of("refund_id", "r.refundId", "processed_at", "r.processedAt"), "r.refundId", true);
    }

    @Transactional(readOnly = true)
    public Refund refund(Long id) {
        return db.get(Refund.class, id, "Refund");
    }

    // ---- fee structures ---------------------------------------------------------------------------------------------

    public FeeStructure createFee(FeeCreate d, CurrentUser user) {
        db.get(ServiceType.class, d.serviceTypeId(), "Service type");
        if (d.effectiveTo() != null && d.effectiveTo().isBefore(d.effectiveFrom())) {
            throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", "effective_to", "message", "effective_to must not be before effective_from")));
        }
        FeeStructure f = new FeeStructure();
        f.setServiceTypeId(d.serviceTypeId());
        f.setComponentName(d.componentName());
        f.setAmount(Fees.money(d.amount()));
        f.setEffectiveFrom(d.effectiveFrom());
        f.setEffectiveTo(d.effectiveTo());
        db.save(f);
        audit.record("fee_structures", f.getFeeId(), "INSERT", null, m("service_type_id", d.serviceTypeId(), "component_name", d.componentName(),
                "amount", f.getAmount(), "effective_from", d.effectiveFrom(), "effective_to", d.effectiveTo()), user.userId());
        db.flush();
        return f;
    }

    public FeeStructure updateFee(Long id, JsonNode body, CurrentUser user) {
        Patch.Parsed<FeeUpdate> pr = patch.parse(body, FeeUpdate.class);
        FeeStructure f = db.lock(FeeStructure.class, id, "Fee structure");
        if (pr.has("effective_to") && pr.dto().effectiveTo() != null && pr.dto().effectiveTo().isBefore(f.getEffectiveFrom())) {
            throw ApiException.badRequest("INVALID_DATES", "effective_to must not be before effective_from");
        }
        Map<String, Object> old = m(), neu = m();
        if (pr.dto().componentName() != null) { old.put("component_name", f.getComponentName()); neu.put("component_name", pr.dto().componentName()); f.setComponentName(pr.dto().componentName()); }
        if (pr.dto().amount() != null) { old.put("amount", f.getAmount()); neu.put("amount", Fees.money(pr.dto().amount())); f.setAmount(Fees.money(pr.dto().amount())); }
        if (pr.has("effective_to")) { old.put("effective_to", f.getEffectiveTo()); neu.put("effective_to", pr.dto().effectiveTo()); f.setEffectiveTo(pr.dto().effectiveTo()); }
        audit.record("fee_structures", id, "UPDATE", old, neu, user.userId());
        db.flush();
        return f;
    }

    @Transactional(readOnly = true)
    public List<FeeStructure> fees(Long serviceTypeId, LocalDate activeOn) {
        StringBuilder q = new StringBuilder("select f from FeeStructure f where 1 = 1");
        java.util.List<Object> kv = new java.util.ArrayList<>();
        if (serviceTypeId != null) { q.append(" and f.serviceTypeId = :s"); kv.add("s"); kv.add(serviceTypeId); }
        if (activeOn != null) { q.append(" and f.effectiveFrom <= :d and (f.effectiveTo is null or f.effectiveTo >= :d)"); kv.add("d"); kv.add(activeOn); }
        return db.list(FeeStructure.class, q + " order by f.serviceTypeId, f.effectiveFrom", kv.toArray());
    }
}
