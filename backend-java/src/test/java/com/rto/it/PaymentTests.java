package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.ApiException;
import com.rto.core.Clock;
import com.rto.core.CurrentUser;
import com.rto.domain.FeeStructure;
import com.rto.domain.PermitType;
import com.rto.domain.RoadTaxRecord;
import com.rto.service.PaymentService;
import com.rto.service.UserLoader;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentTests extends BaseIT {
    @Autowired PaymentService payments;
    @Autowired UserLoader loader;

    Env env;
    long vehicleId;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
        Fx.Refs refs = fx.makeVehicleRefs(true);
        Map<String, Object> b = m("registration_number", "MH12" + Fx.u(6), "chassis_number", "CH" + Fx.u(12), "engine_number", "EN" + Fx.u(12),
                "manufacture_year", 2020, "registering_office_id", env.office.getOfficeId(), "owner_citizen_id", env.citizen.getCitizenId());
        b.putAll(refs.body());
        vehicleId = api.post("/api/v1/vehicles", env.token, b).l("vehicle_id");
    }

    Resp makeChallan(String fine) {
        Resp v = api.post("/api/v1/violations", env.token, m("vehicle_id", vehicleId, "violation_type_id", fx.makeViolationType(fine).getViolationTypeId(),
                "location", "NH48", "occurred_at", LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2).withNano(0).toString()));
        return api.post("/api/v1/challans", env.token, m("violation_ids", List.of(v.l("violation_id"))));
    }

    Resp pay(String type, long id, Object... kv) {
        return api.post("/api/v1/payments", env.token, flows.put(m("payable_type", type, "payable_id", id), kv));
    }

    Resp retry(long paymentId, String reference, String outcome) {
        return api.post("/api/v1/payments/" + paymentId + "/retry", env.token, m("gateway_reference", reference, "outcome", outcome));
    }

    // ------------------------------- creation / targets -------------------------------

    @Test
    void challanPaymentSuccessMarksTheChallanPaidWithHistory() {
        Resp c = makeChallan("750.50");
        Resp r = pay("CHALLAN", c.l("challan_id"), "gateway_reference", flows.ref(), "outcome", "SUCCESS");
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("SUCCESS");
        assertThat(r.s("amount")).isEqualTo("750.50");
        assertThat(r.isNull("paid_at")).isFalse();
        assertThat(r.i("attempts")).isEqualTo(1);
        assertThat(r.s("receipt_number")).startsWith("RCT-");
        assertThat(r.s("refundable_amount")).isEqualTo("750.50");
        assertThat(api.get("/api/v1/challans/" + c.l("challan_id"), env.token).s("status")).isEqualTo("PAID");
        List<String> hist = fx.jdbc.queryForList("SELECT new_status FROM challan_status_history WHERE challan_id = ? ORDER BY history_id", String.class, c.l("challan_id"));
        assertThat(hist).containsExactly("ISSUED", "PAID");
        JsonNode atts = api.get("/api/v1/payments/" + r.l("payment_id") + "/attempts", env.token).json();
        assertThat(atts).hasSize(1);
        assertThat(atts.get(0).get("attempt_number").asInt()).isEqualTo(1);
        assertThat(atts.get(0).get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'payments' AND record_id = ?", r.l("payment_id"))).isGreaterThan(0);
    }

    @Test
    void pendingPaymentWithoutAttemptAndTargetValidation() {
        Resp c = makeChallan("100.00");
        Resp r = pay("CHALLAN", c.l("challan_id"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("PENDING");
        assertThat(r.i("attempts")).isEqualTo(0);
        assertThat(r.isNull("paid_at")).isTrue();
        Resp dup = pay("CHALLAN", c.l("challan_id"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("PAYMENT_PENDING");
        Resp missing = pay("CHALLAN", 99999999);
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.code()).isEqualTo("PAYABLE_NOT_FOUND");
        assertThat(pay("APPLICATION", 99999999).status()).isEqualTo(404);
        assertThat(pay("PERMIT", 99999999).status()).isEqualTo(404);
        assertThat(pay("ROAD_TAX", 99999999).status()).isEqualTo(404);
        assertThat(pay("FINE", c.l("challan_id")).status()).isEqualTo(422);
        assertThat(api.post("/api/v1/payments", env.token, m("payable_type", "CHALLAN", "payable_id", c.l("challan_id"), "outcome", "SUCCESS")).status()).isEqualTo(422);
        assertThat(pay("CHALLAN", c.l("challan_id"), "amount", "1.00").status()).isEqualTo(409);   // a pending payment exists first
    }

    @Test
    void amountMustMatchAndBePositiveDecimal() {
        Resp c = makeChallan("100.10");
        long cid = c.l("challan_id");
        assertThat(pay("CHALLAN", cid, "amount", "100.00").code()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(pay("CHALLAN", cid, "amount", "0").status()).isEqualTo(422);
        assertThat(pay("CHALLAN", cid, "amount", "-5").status()).isEqualTo(422);
        assertThat(pay("CHALLAN", cid, "amount", "100.101").status()).isEqualTo(422);
        Resp ok = pay("CHALLAN", cid, "amount", "100.10");
        assertThat(ok.status()).isEqualTo(201);
        assertThat(new BigDecimal(ok.s("amount"))).isEqualByComparingTo("100.10");
    }

    @Test
    void onlyPayableStatesAcceptPayment() {
        Resp c = makeChallan("100.00");
        long cid = c.l("challan_id");
        api.post("/api/v1/challans/" + cid + "/dispute", env.token, m("reason", "disputed fine"));
        Resp r = pay("CHALLAN", cid);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("NOT_PAYABLE");
        api.post("/api/v1/challans/" + cid + "/cancel", env.token, m("reason", "cancelled it"));
        assertThat(pay("CHALLAN", cid).code()).isEqualTo("NOT_PAYABLE");
        JsonNode app = flows.verifiedApp(env);   // UNDER_VERIFICATION, not AWAITING_PAYMENT
        assertThat(pay("APPLICATION", app.get("application_id").asLong()).code()).isEqualTo("NOT_PAYABLE");
    }

    @Test
    void aSuccessCannotBeProcessedTwiceAndNewPaymentsAreBlocked() {
        Resp c = makeChallan("100.00");
        Resp p = pay("CHALLAN", c.l("challan_id"), "gateway_reference", flows.ref(), "outcome", "SUCCESS");
        Resp again = retry(p.l("payment_id"), flows.ref(), "SUCCESS");
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("PAYMENT_ALREADY_SUCCESSFUL");
        assertThat(pay("CHALLAN", c.l("challan_id")).status()).isEqualTo(409);   // challan is PAID -> NOT_PAYABLE
        assertThat(api.get("/api/v1/payments/" + p.l("payment_id") + "/attempts", env.token).json()).hasSize(1);
    }

    // ------------------------------- idempotency / retries -------------------------------

    @Test
    void gatewayReferenceReplayIsIdempotent() {
        Resp c = makeChallan("100.00");
        String g = flows.ref();
        Resp first = pay("CHALLAN", c.l("challan_id"), "gateway_reference", g, "outcome", "FAILED");
        assertThat(first.s("status")).isEqualTo("FAILED");
        Resp replay = retry(first.l("payment_id"), g, "FAILED");
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.b("idempotent_replay")).isTrue();
        assertThat(fx.count("SELECT COUNT(*) FROM payment_attempts WHERE gateway_reference = ?", g)).isEqualTo(1);
        Resp ok = retry(first.l("payment_id"), flows.ref(), "SUCCESS");
        assertThat(ok.s("status")).isEqualTo("SUCCESS");
        assertThat(ok.i("attempts")).isEqualTo(2);
        Resp replay2 = retry(first.l("payment_id"), g, "FAILED");
        assertThat(replay2.status()).isEqualTo(200);
        assertThat(replay2.s("status")).isEqualTo("SUCCESS");
        assertThat(replay2.b("idempotent_replay")).isTrue();
    }

    @Test
    void resendingTheCreateRequestReturnsTheOriginalPayment() {
        Resp c = makeChallan("100.00");
        String g = flows.ref();
        Resp first = pay("CHALLAN", c.l("challan_id"), "gateway_reference", g, "outcome", "SUCCESS");
        Resp again = pay("CHALLAN", c.l("challan_id"), "gateway_reference", g, "outcome", "SUCCESS");
        assertThat(first.status()).isEqualTo(201);
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.l("payment_id")).isEqualTo(first.l("payment_id"));
        assertThat(again.b("idempotent_replay")).isTrue();
        assertThat(fx.count("SELECT COUNT(*) FROM payments WHERE payable_id = ? AND payable_type_id = (SELECT payable_type_id FROM payable_types WHERE type_name = 'CHALLAN')", c.l("challan_id"))).isEqualTo(1);
    }

    @Test
    void gatewayReferenceCannotBeReusedAcrossPayments() {
        String g = flows.ref();
        Resp c1 = makeChallan("100.00"), c2 = makeChallan("100.00");
        assertThat(pay("CHALLAN", c1.l("challan_id"), "gateway_reference", g, "outcome", "SUCCESS").status()).isEqualTo(201);
        Resp r = pay("CHALLAN", c2.l("challan_id"), "gateway_reference", g, "outcome", "SUCCESS");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("GATEWAY_REFERENCE_REUSED");
        assertThat(api.get("/api/v1/challans/" + c2.l("challan_id"), env.token).s("status")).isEqualTo("ISSUED");
    }

    @Test
    void failedPaymentStaysDistinguishableAndRetryAppendsAttempts() {
        Resp c = makeChallan("100.00");
        Resp p = pay("CHALLAN", c.l("challan_id"), "gateway_reference", flows.ref(), "outcome", "FAILED");
        assertThat(p.s("status")).isEqualTo("FAILED");
        assertThat(p.isNull("paid_at")).isTrue();
        assertThat(p.s("refundable_amount")).isEqualTo("0.00");
        assertThat(api.get("/api/v1/challans/" + c.l("challan_id"), env.token).s("status")).isEqualTo("ISSUED");
        Resp t = retry(p.l("payment_id"), flows.ref(), "TIMEOUT");
        assertThat(t.s("status")).isIn("FAILED", "PENDING");
        Resp fin = retry(p.l("payment_id"), flows.ref(), "SUCCESS");
        assertThat(fin.s("status")).isEqualTo("SUCCESS");
        assertThat(fin.i("attempts")).isEqualTo(3);
        JsonNode atts = api.get("/api/v1/payments/" + p.l("payment_id") + "/attempts", env.token).json();
        List<String> outcomes = new ArrayList<>();
        atts.forEach(a -> outcomes.add(a.get("attempt_number").asInt() + ":" + a.get("outcome").asText()));
        assertThat(outcomes).containsExactly("1:FAILED", "2:TIMEOUT", "3:SUCCESS");
    }

    @Test
    void anOldFailedPaymentCannotSucceedAfterTheItemWasPaidElsewhere() {
        Resp c = makeChallan("100.00");
        Resp failed = pay("CHALLAN", c.l("challan_id"), "gateway_reference", flows.ref(), "outcome", "FAILED");
        Resp second = pay("CHALLAN", c.l("challan_id"), "gateway_reference", flows.ref(), "outcome", "SUCCESS");
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.l("payment_id")).isNotEqualTo(failed.l("payment_id"));
        Resp r = retry(failed.l("payment_id"), flows.ref(), "SUCCESS");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("ALREADY_PAID");
    }

    @Test
    void challanPayEndpointReusesTheOpenPaymentAndIsIdempotent() {
        Resp c = makeChallan("100.00");
        String url = "/api/v1/challans/" + c.l("challan_id") + "/pay";
        Resp f = api.post(url, env.token, m("gateway_reference", flows.ref(), "outcome", "FAILED"));
        assertThat(f.status()).isEqualTo(200);
        assertThat(f.s("status")).isEqualTo("FAILED");
        String g = flows.ref();
        Resp ok = api.post(url, env.token, m("gateway_reference", g, "outcome", "SUCCESS"));
        assertThat(ok.s("status")).isEqualTo("SUCCESS");
        assertThat(ok.l("payment_id")).isEqualTo(f.l("payment_id"));
        assertThat(ok.i("attempts")).isEqualTo(2);
        Resp again = api.post(url, env.token, m("gateway_reference", g, "outcome", "SUCCESS"));
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.b("idempotent_replay")).isTrue();
        assertThat(again.i("attempts")).isEqualTo(2);
        assertThat(api.post(url, env.token, m("gateway_reference", flows.ref(), "outcome", "SUCCESS")).status()).isEqualTo(409);
        assertThat(fx.count("SELECT COUNT(*) FROM payments WHERE payable_id = ? AND payable_type_id = (SELECT payable_type_id FROM payable_types WHERE type_name = 'CHALLAN')", c.l("challan_id"))).isEqualTo(1);
    }

    // ------------------------------- application / permit / road tax targets -------------------------------

    @Test
    void applicationFeeIsBasePlusEffectiveComponentsAndGatesApproval() {
        var service = fx.makeServiceType("500.00", 7);
        addFee(service.getServiceTypeId(), "Smart card", "200.25", Clock.today().minusDays(5), null);
        addFee(service.getServiceTypeId(), "Old fee", "99.00", Clock.today().minusDays(90), Clock.today().minusDays(30));
        addFee(service.getServiceTypeId(), "Future", "77.00", Clock.today().plusDays(30), null);
        env.service = service;
        JsonNode a = flows.verifiedApp(env);
        long aid = a.get("application_id").asLong();
        assertThat(api.get("/api/v1/applications/" + aid, env.token).s("fee_due")).isEqualTo("700.25");
        assertThat(flows.setStatus(env, aid, "AWAITING_PAYMENT", null).status()).isEqualTo(200);
        assertThat(pay("APPLICATION", aid, "amount", "500.00").code()).isEqualTo("AMOUNT_MISMATCH");
        Resp r = pay("APPLICATION", aid, "gateway_reference", flows.ref(), "outcome", "FAILED");
        assertThat(r.s("amount")).isEqualTo("700.25");
        assertThat(r.s("status")).isEqualTo("FAILED");
        assertThat(flows.setStatus(env, aid, "APPROVED", null).code()).isEqualTo("PAYMENT_REQUIRED");   // a failed payment is not payment
        retry(r.l("payment_id"), flows.ref(), "SUCCESS");
        assertThat(flows.setStatus(env, aid, "APPROVED", null).status()).isEqualTo(200);
        JsonNode fees = api.get("/api/v1/fee-structures", env.token, m("service_type_id", service.getServiceTypeId(), "active_on", Clock.today().toString())).json();
        List<String> names = new ArrayList<>();
        fees.forEach(f -> names.add(f.get("component_name").asText()));
        assertThat(names).containsExactly("Smart card");
    }

    private void addFee(Long serviceTypeId, String name, String amount, java.time.LocalDate from, java.time.LocalDate to) {
        FeeStructure f = new FeeStructure();
        f.setServiceTypeId(serviceTypeId);
        f.setComponentName(name);
        f.setAmount(new BigDecimal(amount));
        f.setEffectiveFrom(from);
        f.setEffectiveTo(to);
        fx.save(f);
    }

    @Test
    void aZeroFeeApplicationIsNotPayable() {
        JsonNode a = flows.verifiedApp(env);
        flows.setStatus(env, a.get("application_id").asLong(), "AWAITING_PAYMENT", null);
        Resp r = pay("APPLICATION", a.get("application_id").asLong());
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("NOT_PAYABLE");
    }

    @Test
    void roadTaxPaymentAndRefundRevertsTheStatus() {
        RoadTaxRecord tax = new RoadTaxRecord();
        tax.setVehicleId(vehicleId);
        tax.setAssessmentYear(2040);
        tax.setAmountDue(new BigDecimal("1000.00"));
        tax.setDueDate(Clock.today().plusDays(10));
        tax.setStatus("DUE");
        fx.save(tax);
        Resp p = pay("ROAD_TAX", tax.getTaxRecordId(), "gateway_reference", flows.ref(), "outcome", "SUCCESS");
        assertThat(fx.jdbc.queryForObject("SELECT status FROM road_tax_records WHERE tax_record_id = ?", String.class, tax.getTaxRecordId())).isEqualTo("PAID");
        Resp r = api.post("/api/v1/payments/" + p.l("payment_id") + "/refund", env.token, m("amount", "1000.00", "reason", "Paid in error"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("COMPLETED");
        assertThat(fx.jdbc.queryForObject("SELECT status FROM road_tax_records WHERE tax_record_id = ?", String.class, tax.getTaxRecordId())).isEqualTo("DUE");
        assertThat(api.get("/api/v1/payments/" + p.l("payment_id"), env.token).s("status")).isEqualTo("REFUNDED");
    }

    @Test
    void aPermitPaymentRequiresAnExplicitAmount() {
        PermitType pt = fx.makePermitType(6);
        JsonNode app = flows.approvedApp(env);
        Resp permit = api.post("/api/v1/permits", env.token, m("vehicle_id", vehicleId, "citizen_id", env.citizen.getCitizenId(),
                "permit_type_id", pt.getPermitTypeId(), "application_id", app.get("application_id").asLong()));
        assertThat(permit.status()).isEqualTo(201);
        assertThat(pay("PERMIT", permit.l("permit_id")).code()).isEqualTo("AMOUNT_REQUIRED");
        Resp r = pay("PERMIT", permit.l("permit_id"), "amount", "2500.00", "gateway_reference", flows.ref(), "outcome", "SUCCESS");
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.s("amount")).isEqualTo("2500.00");
        api.post("/api/v1/permits/" + permit.l("permit_id") + "/cancel", env.token, m("reason", "operator request"));
        assertThat(pay("PERMIT", permit.l("permit_id"), "amount", "10.00").code()).isIn("NOT_PAYABLE", "ALREADY_PAID");
    }

    // ------------------------------- refunds -------------------------------

    Resp paid(String fine) {
        Resp c = makeChallan(fine);
        return pay("CHALLAN", c.l("challan_id"), "gateway_reference", flows.ref(), "outcome", "SUCCESS");
    }

    Resp refund(long paymentId, String amount, Object... kv) {
        return api.post("/api/v1/payments/" + paymentId + "/refund", env.token, flows.put(m("amount", amount, "reason", "Customer request"), kv));
    }

    @Test
    void partialThenFullRefundMovesThePaymentToRefunded() {
        Resp p = paid("1000.00");
        long pid = p.l("payment_id");
        Resp r1 = refund(pid, "400.00");
        assertThat(r1.status()).isEqualTo(201);
        assertThat(r1.s("status")).isEqualTo("COMPLETED");
        assertThat(r1.isNull("processed_at")).isFalse();
        Resp got = api.get("/api/v1/payments/" + pid, env.token);
        assertThat(got.s("status")).isEqualTo("SUCCESS");
        assertThat(got.s("refunded_amount")).isEqualTo("400.00");
        assertThat(got.s("refundable_amount")).isEqualTo("600.00");
        Resp over = refund(pid, "600.01");
        assertThat(over.status()).isEqualTo(409);
        assertThat(over.code()).isEqualTo("REFUND_EXCEEDS_PAYMENT");
        assertThat(refund(pid, "600.00").status()).isEqualTo(201);
        got = api.get("/api/v1/payments/" + pid, env.token);
        assertThat(got.s("status")).isEqualTo("REFUNDED");
        assertThat(got.s("refundable_amount")).isEqualTo("0.00");
        assertThat(got.s("refunded_amount")).isEqualTo("1000.00");
        assertThat(refund(pid, "1.00").code()).isEqualTo("NOT_REFUNDABLE");
        assertThat(api.get("/api/v1/payments/" + pid + "/refunds", env.token).json()).hasSize(2);
        Resp retry = retry(pid, flows.ref(), "SUCCESS");
        assertThat(retry.status()).isEqualTo(409);
        assertThat(retry.code()).isEqualTo("PAYMENT_REFUNDED");
    }

    @Test
    void refundValidationAndNonSuccessPayments() {
        Resp p = paid("1000.00");
        long pid = p.l("payment_id");
        assertThat(refund(pid, "0").status()).isEqualTo(422);
        assertThat(refund(pid, "-1").status()).isEqualTo(422);
        assertThat(refund(pid, "10.001").status()).isEqualTo(422);
        assertThat(refund(pid, "10.00", "reason", "x").status()).isEqualTo(422);
        assertThat(refund(99999999, "10.00").status()).isEqualTo(404);
        Resp c = makeChallan("100.00");
        Resp failed = pay("CHALLAN", c.l("challan_id"), "gateway_reference", flows.ref(), "outcome", "FAILED");
        Resp r = refund(failed.l("payment_id"), "10.00");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("NOT_REFUNDABLE");
    }

    @Test
    void onlyOneRefundInFlightAndAFailedRefundReleasesTheAmount() {
        Resp p = paid("1000.00");
        long pid = p.l("payment_id");
        Resp first = refund(pid, "300.00", "auto_complete", false);
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.s("status")).isEqualTo("INITIATED");
        assertThat(first.isNull("processed_at")).isTrue();
        assertThat(api.get("/api/v1/payments/" + pid, env.token).s("refundable_amount")).isEqualTo("700.00");   // reserved
        Resp dup = refund(pid, "300.00", "auto_complete", false);
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("REFUND_IN_PROGRESS");
        long rid = first.l("refund_id");
        assertThat(api.post("/api/v1/refunds/" + rid + "/fail", env.token, m("reason", "bank rejected")).s("status")).isEqualTo("FAILED");
        assertThat(api.get("/api/v1/payments/" + pid, env.token).s("refundable_amount")).isEqualTo("1000.00");   // released
        assertThat(api.post("/api/v1/refunds/" + rid + "/complete", env.token).status()).isEqualTo(409);
        Resp second = refund(pid, "1000.00", "auto_complete", false);
        assertThat(api.post("/api/v1/refunds/" + second.l("refund_id") + "/complete", env.token).s("status")).isEqualTo("COMPLETED");
        assertThat(api.get("/api/v1/payments/" + pid, env.token).s("status")).isEqualTo("REFUNDED");
        assertThat(api.get("/api/v1/refunds", env.token, m("payment_id", pid, "status", "FAILED")).l("total")).isEqualTo(1);
    }

    @Test
    void refundAndPaymentPermissions() {
        Resp p = paid("1000.00");
        String viewer = fx.withPermissions("payment.view");
        assertThat(api.get("/api/v1/payments/" + p.l("payment_id"), viewer).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/payments/" + p.l("payment_id") + "/refund", viewer, m("amount", "10.00", "reason", "no rights")).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/payments", viewer, m("payable_type", "CHALLAN", "payable_id", 1)).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/payments", null).status()).isEqualTo(401);
    }

    @Test
    void listingFilters() {
        Resp p = paid("1000.00");
        java.util.function.Function<Map<String, Object>, List<Long>> q = params -> {
            List<Long> ids = new ArrayList<>();
            api.get("/api/v1/payments", env.token, params).json().get("items").forEach(i -> ids.add(i.get("payment_id").asLong()));
            return ids;
        };
        assertThat(q.apply(m("payable_type", "CHALLAN", "payable_id", p.l("payable_id"), "status", "SUCCESS"))).containsExactly(p.l("payment_id"));
        assertThat(q.apply(m("search", p.s("receipt_number")))).containsExactly(p.l("payment_id"));
        assertThat(q.apply(m("payable_type", "CHALLAN", "payable_id", p.l("payable_id"), "status", "FAILED"))).isEmpty();
        assertThat(q.apply(m("date_from", Clock.today().toString(), "payable_id", p.l("payable_id")))).containsExactly(p.l("payment_id"));
    }

    // ------------------------------- concurrency -------------------------------

    @Test
    void concurrentSuccessAttemptsSettleExactlyOnce() throws Exception {
        Resp c = makeChallan("100.00");
        Resp p = pay("CHALLAN", c.l("challan_id"));
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int n = i;
            Thread t = new Thread(() -> {
                try {
                    CurrentUser user = loader.load(env.user.getUserId());
                    start.await();
                    payments.retry(p.l("payment_id"), "CONC-" + Fx.u(10) + "-" + n, "SUCCESS", user);
                    results.add("ok");
                } catch (ApiException e) {
                    results.add(e.code());
                } catch (Exception e) {
                    results.add("ERR " + e);
                }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) t.join();
        assertThat(results.stream().filter("ok"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter("PAYMENT_ALREADY_SUCCESSFUL"::equals).count()).isEqualTo(7);
        assertThat(fx.count("SELECT COUNT(*) FROM payment_attempts WHERE payment_id = ?", p.l("payment_id"))).isEqualTo(1);
        assertThat(fx.count("SELECT COUNT(*) FROM challan_status_history WHERE challan_id = ? AND new_status = 'PAID'", c.l("challan_id"))).isEqualTo(1);
    }

    @Test
    void concurrentRefundsNeverExceedThePayment() throws Exception {
        Resp p = paid("900.00");
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Thread t = new Thread(() -> {
                try {
                    CurrentUser user = loader.load(env.user.getUserId());
                    start.await();
                    payments.createRefund(p.l("payment_id"), new BigDecimal("400.00"), "race", true, user);
                    results.add("ok");
                } catch (ApiException e) {
                    results.add(e.code());
                } catch (Exception e) {
                    results.add("ERR " + e);
                }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) t.join();
        assertThat(results.stream().filter("ok"::equals).count()).isEqualTo(2);   // 400 + 400 fits in 900; a third would not
        assertThat(results).allMatch(r -> r.equals("ok") || r.equals("REFUND_EXCEEDS_PAYMENT") || r.equals("REFUND_IN_PROGRESS"));
        BigDecimal total = fx.jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE payment_id = ? AND status = 'COMPLETED'", BigDecimal.class, p.l("payment_id"));
        assertThat(total).isEqualByComparingTo("800.00");
    }
}
