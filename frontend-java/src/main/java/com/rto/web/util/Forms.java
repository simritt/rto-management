package com.rto.web.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** Helpers for turning HTML form posts into API request bodies and API validation errors into per-field messages. */
public final class Forms {
    private Forms() {}

    /** Non-blank params whose names match no dot-prefix; blanks are dropped (use for create bodies). */
    public static Map<String, Object> compact(Map<String, String> params, String... keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : keys) {
            String v = params.get(k);
            if (v != null && !v.isBlank()) {
                out.put(k, v.trim());
            }
        }
        return out;
    }

    /** Like compact, but blank optional fields become explicit nulls so a PATCH can clear them. */
    public static Map<String, Object> patch(Map<String, String> params, String[] required, String[] optional) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : required) {
            String v = params.get(k);
            if (v != null && !v.isBlank()) {
                out.put(k, v.trim());
            }
        }
        for (String k : optional) {
            String v = params.get(k);
            out.put(k, v == null || v.isBlank() ? null : v.trim());
        }
        return out;
    }

    /** API errors [{field, message}] -> {field: message} (first message per field wins). */
    public static Map<String, String> fieldErrors(JsonNode errors) {
        Map<String, String> out = new LinkedHashMap<>();
        if (errors != null && errors.isArray()) {
            errors.forEach(e -> out.putIfAbsent(e.path("field").asText(), e.path("message").asText()));
        }
        return out;
    }
}
