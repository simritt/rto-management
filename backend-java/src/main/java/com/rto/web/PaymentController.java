package com.rto.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.PaymentDto.*;
import com.rto.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "payments")
public class PaymentController {
    private final PaymentService svc;

    public PaymentController(PaymentService svc) {
        this.svc = svc;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("payment.create")
    @Operation(summary = "Create a payment for an APPLICATION, CHALLAN, PERMIT or ROAD_TAX (target is validated)")
    public PaymentOut create(@Valid @RequestBody PaymentCreate body, CurrentUser user) {
        return svc.create(body, user);
    }

    @GetMapping("/payments")
    @Requires("payment.view")
    public PageResponse<PaymentOut> list(@RequestParam(required = false) @Pattern(regexp = "^[A-Z]+$") String status,
                                         @RequestParam(name = "payable_type", required = false) @Pattern(regexp = com.rto.dto.PaymentDto.PAYABLE) String payableType,
                                         @RequestParam(name = "payable_id", required = false) Long payableId,
                                         @RequestParam(name = "date_from", required = false) LocalDate from,
                                         @RequestParam(name = "date_to", required = false) LocalDate to, PageParams p) {
        return svc.list(p, status, payableType, payableId, from, to);
    }

    @GetMapping("/payments/{paymentId}")
    @Requires("payment.view")
    public PaymentOut get(@PathVariable Long paymentId) {
        return svc.view(paymentId);
    }

    @GetMapping("/payments/{paymentId}/attempts")
    @Requires("payment.view")
    @Operation(summary = "Attempt history (append-only)")
    public List<PaymentAttempt> attempts(@PathVariable Long paymentId) {
        return svc.attempts(paymentId);
    }

    @PostMapping("/payments/{paymentId}/retry")
    @Requires("payment.create")
    @Operation(summary = "Record another gateway attempt; replaying the same gateway_reference is idempotent")
    public PaymentOut retry(@PathVariable Long paymentId, @Valid @RequestBody AttemptIn body, CurrentUser user) {
        return svc.retry(paymentId, body.gatewayReference(), body.outcome(), user);
    }

    @PostMapping("/payments/{paymentId}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("payment.refund")
    @Operation(summary = "Refund up to the refundable amount; one refund in flight at a time")
    public Refund refund(@PathVariable Long paymentId, @Valid @RequestBody RefundCreate body, CurrentUser user) {
        return svc.createRefund(paymentId, body.amount(), body.reason(), body.autoComplete(), user);
    }

    @GetMapping("/payments/{paymentId}/refunds")
    @Requires("payment.view")
    public List<Refund> paymentRefunds(@PathVariable Long paymentId) {
        svc.view(paymentId);
        return svc.refunds(PageParams.of(1, 100), paymentId, null).items();
    }

    @GetMapping("/refunds")
    @Requires("payment.view")
    public PageResponse<Refund> refunds(@RequestParam(name = "payment_id", required = false) Long paymentId,
                                        @RequestParam(required = false) @Pattern(regexp = "^[A-Z]+$") String status, PageParams p) {
        return svc.refunds(p, paymentId, status);
    }

    @GetMapping("/refunds/{refundId}")
    @Requires("payment.view")
    public Refund refundById(@PathVariable Long refundId) {
        return svc.refund(refundId);
    }

    @PostMapping("/refunds/{refundId}/complete")
    @Requires("payment.refund")
    @Operation(summary = "INITIATED -> COMPLETED")
    public Refund complete(@PathVariable Long refundId, CurrentUser user) {
        return svc.completeRefund(refundId, user);
    }

    @PostMapping("/refunds/{refundId}/fail")
    @Requires("payment.refund")
    @Operation(summary = "INITIATED -> FAILED (releases the amount)")
    public Refund fail(@PathVariable Long refundId, @RequestBody(required = false) RefundFail body, CurrentUser user) {
        return svc.failRefund(refundId, body == null ? null : body.reason(), user);
    }

    @PostMapping("/challans/{challanId}/pay")
    @Requires("payment.create")
    @Operation(summary = "Pay a challan: creates/reuses its payment, records the attempt, marks the challan PAID")
    public PaymentOut payChallan(@PathVariable Long challanId, @Valid @RequestBody AttemptIn body, CurrentUser user) {
        return svc.payTarget("CHALLAN", challanId, body.gatewayReference(), body.outcome(), user);
    }

    // ---- fee structures ---------------------------------------------------------------------------------

    @GetMapping("/fee-structures")
    @Operation(summary = "Fee components per service type")
    public List<FeeStructure> fees(@RequestParam(name = "service_type_id", required = false) Long serviceTypeId,
                                   @RequestParam(name = "active_on", required = false) LocalDate activeOn) {
        return svc.fees(serviceTypeId, activeOn);
    }

    @PostMapping("/fee-structures")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("reference.manage")
    public FeeStructure createFee(@Valid @RequestBody FeeCreate body, CurrentUser user) {
        return svc.createFee(body, user);
    }

    @PatchMapping("/fee-structures/{feeId}")
    @Requires("reference.manage")
    public FeeStructure updateFee(@PathVariable Long feeId, @RequestBody JsonNode body, CurrentUser user) {
        return svc.updateFee(feeId, body, user);
    }
}
