package com.rto.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JPQL builder for filtered/paginated lists: conditions are appended only when their value is present, every
 * value is a bound parameter (never concatenated), and sort columns come from a whitelist.
 */
public final class QB {
    private final String alias;
    private final String from;
    private final List<String> where = new ArrayList<>();
    final Map<String, Object> params = new LinkedHashMap<>();
    private int n = 0;
    private String selectExpr;

    /** Select several aliases at once (e.g. "a, c, p"); read the page with Object[].class. */
    public QB select(String expr) {
        this.selectExpr = expr;
        return this;
    }

    public QB(String alias, String from) {
        this.alias = alias;
        this.from = from;
    }

    public String alias() { return alias; }

    /** Always-applied condition with explicit named parameters ("name", value, ...). */
    public QB and(String cond, Object... kv) {
        where.add(cond);
        for (int i = 0; i < kv.length; i += 2) params.put((String) kv[i], kv[i + 1]);
        return this;
    }

    /** `path = :p` only when value is non-null. */
    public QB eq(String path, Object value) {
        if (value != null) {
            String p = "p" + (n++);
            where.add(path + " = :" + p);
            params.put(p, value);
        }
        return this;
    }

    /** Arbitrary operator ("<", ">=", "like", ...) only when value is non-null. */
    public QB op(String path, String op, Object value) {
        if (value != null) {
            String p = "p" + (n++);
            where.add(path + " " + op + " :" + p);
            params.put(p, value);
        }
        return this;
    }

    /** `(c1 like :t or c2 like :t ...)` when a search term is present. */
    public QB search(String term, String... paths) {
        if (term != null && !term.isBlank()) {
            String p = "p" + (n++);
            List<String> ors = new ArrayList<>();
            for (String path : paths) ors.add(path + " like :" + p + " escape '\\'");
            where.add("(" + String.join(" or ", ors) + ")");
            params.put(p, like(term));
        }
        return this;
    }

    public static String like(String term) {
        return "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    String whereClause() {
        return where.isEmpty() ? "" : " where " + String.join(" and ", where);
    }

    String selectJpql() { return "select " + (selectExpr != null ? selectExpr : alias) + " from " + from + whereClause(); }

    String countJpql() { return "select count(" + alias + ") from " + from + whereClause(); }
}
