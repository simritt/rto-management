package com.rto.web.util;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** Helpers for list pages: building the base URL (all filters, no page) that the pager appends "page=N" to. */
public final class Paging {
    private Paging() {}

    /** e.g. "/citizens?search=raj&amp;" - ready for the pager fragment to append "page=2". */
    public static String base(String path, Map<String, ?> params) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        params.forEach((k, v) -> {
            if (v != null && !v.toString().isBlank() && !k.equals("page")) {
                b.queryParam(k, v);
            }
        });
        String url = b.build().encode().toUriString();
        return url + (url.contains("?") ? "&" : "?");
    }
}
