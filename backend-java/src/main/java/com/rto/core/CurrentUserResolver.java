package com.rto.core;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injects the authenticated {@link CurrentUser} (set by the JWT filter, verified by {@link AuthInterceptor}). */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {
    public static final String ATTR = "rto.user";
    public static final String ERROR_ATTR = "rto.authError";

    @Override
    public boolean supportsParameter(MethodParameter p) {
        return p.getParameterType() == CurrentUser.class;
    }

    @Override
    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest req, WebDataBinderFactory f) {
        Object u = req.getNativeRequest(HttpServletRequest.class).getAttribute(ATTR);
        if (u == null) throw ApiException.unauthorized("NOT_AUTHENTICATED", "Not authenticated");
        return u;
    }
}
