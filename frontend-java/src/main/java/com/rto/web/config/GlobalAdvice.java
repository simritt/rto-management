package com.rto.web.config;

import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Shared template data (current user, permissions) and API-error handling for every page. */
@ControllerAdvice
public class GlobalAdvice {
    private final UserSession session;

    public GlobalAdvice(UserSession session) {
        this.session = session;
    }

    @ModelAttribute
    void common(Model model, HttpServletRequest req) {
        model.addAttribute("user", session);
        model.addAttribute("currentPath", req.getRequestURI());
    }

    @ExceptionHandler(ApiException.class)
    String apiError(ApiException e, Model model, HttpServletRequest req) {
        if (e.status() == 401) {
            return "redirect:/login?expired";
        }
        model.addAttribute("status", e.status());
        model.addAttribute("detail", e.getMessage());
        model.addAttribute("errors", e.errors());
        return "error-page";
    }

    @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class)
    String backendDown(Model model) {
        model.addAttribute("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute("detail", "Cannot reach the RTO backend API. Is it running on port 8000?");
        model.addAttribute("errors", null);
        return "error-page";
    }
}
