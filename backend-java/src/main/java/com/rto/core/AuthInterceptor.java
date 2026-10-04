package com.rto.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Enforces authentication (401) and database-driven RBAC (403) from the @Public / @Requires / @RequiresAny
 * annotations, before any controller code runs. The frontend never decides what a user may do.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;
        if (find(hm, Public.class) != null) return true;

        Object err = req.getAttribute(CurrentUserResolver.ERROR_ATTR);
        if (err instanceof ApiException e) throw e;
        CurrentUser user = (CurrentUser) req.getAttribute(CurrentUserResolver.ATTR);
        if (user == null) throw ApiException.unauthorized("NOT_AUTHENTICATED", "Not authenticated");

        Requires all = find(hm, Requires.class);
        if (all != null) for (String p : all.value()) user.require(p);
        RequiresAny any = find(hm, RequiresAny.class);
        if (any != null && Arrays.stream(any.value()).noneMatch(user::has)) {
            throw ApiException.forbidden("PERMISSION_DENIED",
                    "Missing required permission: one of " + String.join(", ", any.value()));
        }
        return true;
    }

    private static <A extends java.lang.annotation.Annotation> A find(HandlerMethod hm, Class<A> type) {
        A a = hm.getMethodAnnotation(type);
        return a != null ? a : hm.getBeanType().getAnnotation(type);
    }
}
