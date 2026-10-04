package com.rto.core;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Binds and validates page, page_size (1..100), search (<=100 chars), sort, order (asc|desc); 422 otherwise. */
@Component
public class PageParamsResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter p) {
        return p.getParameterType() == PageParams.class;
    }

    @Override
    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest req, WebDataBinderFactory f) {
        List<Map<String, String>> errors = new ArrayList<>();
        int page = intParam(req, "page", 1, 1, Integer.MAX_VALUE, errors);
        int size = intParam(req, "page_size", 20, 1, PageParams.MAX_PAGE_SIZE, errors);
        String search = req.getParameter("search");
        if (search != null && search.length() > 100) errors.add(Map.of("field", "search", "message", "must be at most 100 characters"));
        String order = req.getParameter("order") == null ? "asc" : req.getParameter("order");
        if (!order.equals("asc") && !order.equals("desc")) errors.add(Map.of("field", "order", "message", "must be asc or desc"));
        if (!errors.isEmpty()) throw ApiException.unprocessable("Request validation failed", errors);
        return new PageParams(page, size, search == null || search.isBlank() ? null : search.strip(),
                req.getParameter("sort"), order);
    }

    private static int intParam(NativeWebRequest req, String name, int def, int min, int max, List<Map<String, String>> errors) {
        String raw = req.getParameter(name);
        if (raw == null) return def;
        try {
            int v = Integer.parseInt(raw);
            if (v < min || v > max) errors.add(Map.of("field", name, "message", "must be between " + min + " and " + max));
            return Math.min(Math.max(v, min), max);
        } catch (NumberFormatException e) {
            errors.add(Map.of("field", name, "message", "must be an integer"));
            return def;
        }
    }
}
