package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

@Controller
public class PaymentController {

    private final ApiClient api;
    private final UserSession session;

    public PaymentController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    // =========================================================================
    // 1. PAYMENTS (/payments)
    // =========================================================================

    @GetMapping("/payments")
    public String listPayments(
            @RequestParam(required = false) String status,
            @RequestParam(name = "payable_type", required = false) String payableType,
            @RequestParam(name = "payable_id", required = false) Long payableId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (status != null && !status.isBlank()) query.put("status", status.trim());
        if (payableType != null && !payableType.isBlank()) query.put("payable_type", payableType.trim());
        if (payableId != null) query.put("payable_id", payableId);
        if (dateFrom != null && !dateFrom.isBlank()) query.put("date_from", dateFrom.trim());
        if (dateTo != null && !dateTo.isBlank()) query.put("date_to", dateTo.trim());

        JsonNode response = api.get("/payments", query);
        model.addAttribute("data", response);

        model.addAttribute("paramStatus", status);
        model.addAttribute("paramPayableType", payableType);
        model.addAttribute("paramPayableId", payableId);
        model.addAttribute("paramDateFrom", dateFrom);
        model.addAttribute("paramDateTo", dateTo);

        return "payments/list";
    }

    @GetMapping("/payments/new")
    public String newPaymentForm(
            @RequestParam(name = "payable_type", required = false) String payableType,
            @RequestParam(name = "payable_id", required = false) Long payableId,
            @RequestParam(name = "amount", required = false) String amount,
            Model model) {
        requireAuth();

        model.addAttribute("prePayableType", payableType);
        model.addAttribute("prePayableId", payableId);
        model.addAttribute("preAmount", amount);

        return "payments/form";
    }

    @PostMapping("/payments")
    public String createPayment(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payable_type", params.get("payable_type").trim());
            body.put("payable_id", Long.parseLong(params.get("payable_id")));
            body.put("amount", new BigDecimal(params.get("amount").trim()));

            String ref = params.get("gateway_reference");
            if (ref != null && !ref.isBlank()) {
                body.put("gateway_reference", ref.trim());
            } else {
                body.put("gateway_reference", "GW-PAY-" + System.currentTimeMillis());
            }

            String outcome = params.get("outcome");
            if (outcome != null && !outcome.isBlank()) {
                body.put("outcome", outcome.trim());
            } else {
                body.put("outcome", "SUCCESS");
            }

            JsonNode created = api.post("/payments", body);
            long id = created.path("payment_id").asLong();
            ra.addFlashAttribute("flash", "Payment processed (#" + created.path("receipt_number").asText() + ")");
            return "redirect:/payments/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/payments/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form input: " + e.getMessage());
            return "redirect:/payments/new";
        }
    }

    @GetMapping("/payments/{id}")
    public String viewPayment(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode payment = api.get("/payments/" + id);
        model.addAttribute("payment", payment);

        try {
            model.addAttribute("attempts", api.get("/payments/" + id + "/attempts"));
        } catch (Exception ignored) {}

        try {
            model.addAttribute("refunds", api.get("/payments/" + id + "/refunds"));
        } catch (Exception ignored) {}

        return "payments/detail";
    }

    @PostMapping("/payments/{id}/retry")
    public String retryPayment(
            @PathVariable Long id,
            @RequestParam(name = "gateway_reference", required = false) String gatewayRef,
            @RequestParam(name = "outcome", defaultValue = "SUCCESS") String outcome,
            RedirectAttributes ra) {
        requireAuth();

        try {
            String ref = (gatewayRef != null && !gatewayRef.isBlank()) ? gatewayRef.trim() : "RETRY-" + System.currentTimeMillis();
            api.post("/payments/" + id + "/retry", Map.of("gateway_reference", ref, "outcome", outcome.trim()));
            ra.addFlashAttribute("flash", "Payment attempt registered with outcome: " + outcome);
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/payments/" + id;
    }

    @PostMapping("/payments/{id}/refund")
    public String refundPayment(
            @PathVariable Long id,
            @RequestParam("amount") String amountStr,
            @RequestParam("reason") String reason,
            @RequestParam(name = "auto_complete", defaultValue = "true") Boolean autoComplete,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", new BigDecimal(amountStr.trim()));
            body.put("reason", reason.trim());
            body.put("auto_complete", autoComplete);

            JsonNode refund = api.post("/payments/" + id + "/refund", body);
            ra.addFlashAttribute("flash", "Refund processed (ID #" + refund.path("refund_id").asLong() + ")");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/payments/" + id;
    }

    // =========================================================================
    // 2. REFUNDS (/refunds)
    // =========================================================================

    @GetMapping("/refunds")
    public String listRefunds(
            @RequestParam(name = "payment_id", required = false) Long paymentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (paymentId != null) query.put("payment_id", paymentId);
        if (status != null && !status.isBlank()) query.put("status", status.trim());

        JsonNode response = api.get("/refunds", query);
        model.addAttribute("data", response);

        model.addAttribute("paramPaymentId", paymentId);
        model.addAttribute("paramStatus", status);

        return "refunds/list";
    }

    @GetMapping("/refunds/{id}")
    public String viewRefund(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode refund = api.get("/refunds/" + id);
        model.addAttribute("refund", refund);

        long paymentId = refund.path("payment_id").asLong();
        try {
            model.addAttribute("payment", api.get("/payments/" + paymentId));
        } catch (Exception ignored) {}

        return "refunds/detail";
    }

    @PostMapping("/refunds/{id}/complete")
    public String completeRefund(@PathVariable Long id, RedirectAttributes ra) {
        requireAuth();
        try {
            api.post("/refunds/" + id + "/complete", Map.of());
            ra.addFlashAttribute("flash", "Refund marked as COMPLETED.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/refunds/" + id;
    }

    @PostMapping("/refunds/{id}/fail")
    public String failRefund(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            RedirectAttributes ra) {
        requireAuth();
        try {
            Map<String, Object> body = (reason != null && !reason.isBlank()) ? Map.of("reason", reason.trim()) : Map.of();
            api.post("/refunds/" + id + "/fail", body);
            ra.addFlashAttribute("flash", "Refund marked as FAILED.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/refunds/" + id;
    }

    // =========================================================================
    // 3. FEE STRUCTURES (/fees)
    // =========================================================================

    @GetMapping("/fees")
    public String listFees(
            @RequestParam(name = "service_type_id", required = false) Long serviceTypeId,
            @RequestParam(name = "active_on", required = false) String activeOn,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        if (serviceTypeId != null) query.put("service_type_id", serviceTypeId);
        if (activeOn != null && !activeOn.isBlank()) query.put("active_on", activeOn.trim());

        JsonNode fees = api.get("/fee-structures", query);
        model.addAttribute("fees", fees);

        try {
            model.addAttribute("serviceTypes", api.get("/service-types").path("items"));
        } catch (Exception ignored) {}

        model.addAttribute("paramServiceTypeId", serviceTypeId);
        model.addAttribute("paramActiveOn", activeOn);

        return "fees/list";
    }

    @PostMapping("/fees")
    public String createFee(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("service_type_id", Long.parseLong(params.get("service_type_id")));
            body.put("component_name", params.get("component_name").trim());
            body.put("amount", new BigDecimal(params.get("amount").trim()));
            body.put("effective_from", params.get("effective_from").trim());

            String to = params.get("effective_to");
            if (to != null && !to.isBlank()) {
                body.put("effective_to", to.trim());
            }

            api.post("/fee-structures", body);
            ra.addFlashAttribute("flash", "Fee structure component added successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid fee input: " + e.getMessage());
        }
        return "redirect:/fees";
    }

    @PostMapping("/fees/{id}")
    public String updateFee(
            @PathVariable Long id,
            @RequestParam(name = "component_name", required = false) String name,
            @RequestParam(name = "amount", required = false) String amount,
            @RequestParam(name = "effective_to", required = false) String effectiveTo,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) body.put("component_name", name.trim());
            if (amount != null && !amount.isBlank()) body.put("amount", new BigDecimal(amount.trim()));
            if (effectiveTo != null && !effectiveTo.isBlank()) body.put("effective_to", effectiveTo.trim());

            api.patch("/fee-structures/" + id, body);
            ra.addFlashAttribute("flash", "Fee structure component updated successfully.");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/fees";
    }
}
