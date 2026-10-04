package com.rto.config;

import com.rto.core.AuthInterceptor;
import com.rto.core.CurrentUserResolver;
import com.rto.core.PageParamsResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor auth;
    private final CurrentUserResolver users;
    private final PageParamsResolver pages;

    public WebConfig(AuthInterceptor auth, CurrentUserResolver users, PageParamsResolver pages) {
        this.auth = auth;
        this.users = users;
        this.pages = pages;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auth).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(users);
        resolvers.add(pages);
    }
}
