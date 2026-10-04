package com.rto.security;

import com.rto.core.ApiException;
import com.rto.core.CurrentUserResolver;
import com.rto.service.UserLoader;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads "Authorization: Bearer ..." and attaches the CurrentUser (or the precise auth error) to the request.
 * It never rejects by itself: public endpoints must keep working with a bad token, and the AuthInterceptor
 * decides per endpoint (401 for protected ones, with the specific code).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final JwtService jwt;
    private final UserLoader loader;

    public JwtAuthFilter(JwtService jwt, UserLoader loader) {
        this.jwt = jwt;
        this.loader = loader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                Claims claims = jwt.decode(header.substring(7).trim(), "access");
                req.setAttribute(CurrentUserResolver.ATTR, loader.load(Long.parseLong(claims.getSubject())));
                req.setAttribute("rto.claims", claims);
            } catch (ApiException e) {
                req.setAttribute(CurrentUserResolver.ERROR_ATTR, e);
            } catch (Exception e) {
                log.error("Could not authenticate request", e);
                req.setAttribute(CurrentUserResolver.ERROR_ATTR, ApiException.internal());
            }
        }
        chain.doFilter(req, res);
    }
}
