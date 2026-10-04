package com.rto.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permission catalogue and default role grants - SEED DATA for permissions / roles / role_permissions.
 * At runtime the database is authoritative (administrators change roles through the RBAC API); nothing here is
 * consulted for an authorisation decision. Generated from the reference backend so both stay identical.
 */
public final class Perms {
    private Perms() {}

    public static final List<String> ALL = List.of(
            "rbac.manage",
            "user.view",
            "user.manage",
            "audit.view",
            "dashboard.view",
            "reference.manage",
            "citizen.view",
            "citizen.create",
            "citizen.update",
            "citizen.view_sensitive",
            "office.manage",
            "employee.view",
            "employee.manage",
            "application.view",
            "application.view_own",
            "application.create",
            "application.create_own",
            "application.update",
            "application.assign",
            "application.verify",
            "application.approve",
            "application.reject",
            "application.cancel",
            "document.upload",
            "document.view",
            "document.verify",
            "appointment.view",
            "appointment.manage",
            "appointment.book",
            "licence.view",
            "licence.issue",
            "licence.suspend",
            "licence.revoke",
            "licence.test",
            "licence.manage_schools",
            "vehicle.view",
            "vehicle.create",
            "vehicle.update",
            "vehicle.transfer",
            "vehicle.transfer_approve",
            "compliance.view",
            "compliance.manage",
            "permit.view",
            "permit.create",
            "permit.manage",
            "route.manage",
            "violation.view",
            "violation.create",
            "challan.view",
            "challan.create",
            "challan.manage",
            "payment.view",
            "payment.create",
            "payment.refund",
            "complaint.create",
            "complaint.view_own",
            "complaint.manage",
            "appeal.create",
            "appeal.view_own",
            "appeal.review",
            "notification.view",
            "notification.view_own");

    public static final Map<String, Set<String>> ROLE_GRANTS = roles();

    public static final List<String> PAYABLE_TYPES = List.of("APPLICATION", "CHALLAN", "PERMIT", "ROAD_TAX");

    private static Map<String, Set<String>> roles() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("ADMIN", new LinkedHashSet<>(ALL));
        m.put("RTO_OFFICER", new LinkedHashSet<>(List.of("appeal.review", "application.approve", "application.assign", "application.cancel", "application.create", "application.reject", "application.update", "application.verify", "application.view", "appointment.book", "appointment.manage", "appointment.view", "challan.view", "citizen.create", "citizen.update", "citizen.view", "complaint.manage", "compliance.manage", "compliance.view", "dashboard.view", "document.upload", "document.verify", "document.view", "employee.view", "licence.issue", "licence.revoke", "licence.suspend", "licence.test", "licence.view", "notification.view", "payment.view", "permit.create", "permit.manage", "permit.view", "route.manage", "vehicle.create", "vehicle.transfer", "vehicle.transfer_approve", "vehicle.update", "vehicle.view", "violation.view")));
        m.put("COUNTER_CLERK", new LinkedHashSet<>(List.of("application.create", "application.update", "application.view", "appointment.book", "appointment.manage", "appointment.view", "challan.view", "citizen.create", "citizen.update", "citizen.view", "complaint.create", "compliance.view", "dashboard.view", "document.upload", "document.view", "licence.view", "payment.create", "payment.view", "permit.view", "vehicle.transfer", "vehicle.view")));
        m.put("INSPECTOR", new LinkedHashSet<>(List.of("citizen.view", "compliance.manage", "compliance.view", "licence.test", "licence.view", "vehicle.view", "violation.create", "violation.view")));
        m.put("ENFORCEMENT_OFFICER", new LinkedHashSet<>(List.of("challan.create", "challan.manage", "challan.view", "citizen.view", "licence.view", "vehicle.view", "violation.create", "violation.view")));
        m.put("ACCOUNTANT", new LinkedHashSet<>(List.of("application.view", "audit.view", "challan.view", "dashboard.view", "payment.create", "payment.refund", "payment.view")));
        m.put("CITIZEN", new LinkedHashSet<>(List.of("appeal.create", "appeal.view_own", "application.create_own", "application.view_own", "appointment.book", "complaint.create", "complaint.view_own", "document.upload", "notification.view_own")));
        return m;
    }
}
