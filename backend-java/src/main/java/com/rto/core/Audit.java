package com.rto.core;

import com.rto.domain.AuditLog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only audit trail. record() only adds a row to the CALLER's transaction, so an audit entry exists if and
 * only if the audited change committed. There is deliberately no update/delete API for audit_logs.
 */
@Service
public class Audit {
    /** Never written to audit_logs, whatever the caller passes (substring match on the lower-cased key). */
    private static final List<String> REDACTED = List.of("password", "secret", "access_token", "refresh_token", "national_id_number");
    private final Db db;

    public Audit(Db db) {
        this.db = db;
    }

    /** Null-tolerant map literal: m("status", "ACTIVE", "reason", null). */
    public static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    public void record(String table, Long recordId, String action, Map<String, Object> oldValues,
                       Map<String, Object> newValues, Long userId) {
        AuditLog a = new AuditLog();
        a.setTableName(table);
        a.setRecordId(recordId);
        a.setAction(action);
        a.setOldValues(oldValues == null ? null : clean(oldValues));
        a.setNewValues(newValues == null ? null : clean(newValues));
        a.setChangedByUserId(userId);
        a.setChangedAt(Clock.now());
        db.save(a);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> clean(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            String lk = k.toLowerCase();
            if (REDACTED.stream().noneMatch(lk::contains)) out.put(k, jsonable(v));
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    static Object jsonable(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b.toPlainString();
        if (v instanceof LocalDateTime t) return t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (v instanceof LocalDate d) return d.toString();
        if (v instanceof LocalTime t) return t.format(DateTimeFormatter.ISO_LOCAL_TIME);
        if (v instanceof Map<?, ?> m) return clean((Map<String, Object>) m);
        if (v instanceof Collection<?> c) return c.stream().map(Audit::jsonable).toList();
        return v;
    }
}
