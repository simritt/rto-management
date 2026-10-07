package com.rto.web.config;

import com.rto.web.api.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sends anonymous browsers to /login for every page except the login form and static assets. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final UserSession session;

    public WebConfig(UserSession session) {
        this.session = session;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                if (session.isLoggedIn()) {
                    return true;
                }
                res.sendRedirect(req.getContextPath() + "/login");
                return false;
            }
        }).addPathPatterns("/**").excludePathPatterns("/login", "/css/**", "/js/**", "/error", "/favicon.ico");
    }
}
