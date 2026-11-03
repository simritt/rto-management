package com.rto.web.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Shared template data (current user, permissions) and API-error handling for every page. */
@ControllerAdvice
public class GlobalAdvice {
    private static final Logger log = LoggerFactory.getLogger(GlobalAdvice.class);

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
    String apiError(ApiException e, Model model, HttpServletRequest req, HttpServletResponse res) {
        if (e.status() == 401) {
            return "redirect:/login?expired";
        }
        return errorPage(model, req, res, e.status(), friendly(e), e.errors());
    }

    @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class)
    String backendDown(Model model, HttpServletRequest req, HttpServletResponse res) {
        return errorPage(model, req, res, HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Cannot reach the RTO backend API. Is it running on port 8000?", null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    String badRequest(Exception e, Model model, HttpServletRequest req, HttpServletResponse res) {
        return errorPage(model, req, res, HttpStatus.BAD_REQUEST.value(),
                "That request had a missing or invalid value.", null);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    String wrongMethod(Model model, HttpServletRequest req, HttpServletResponse res) {
        return errorPage(model, req, res, HttpStatus.METHOD_NOT_ALLOWED.value(),
                "That address cannot be opened directly.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    String notFound(Model model, HttpServletRequest req, HttpServletResponse res) {
        return errorPage(model, req, res, HttpStatus.NOT_FOUND.value(), "That page does not exist.", null);
    }

    /** Last resort: log the details server-side, show a generic message, never a stack trace. */
    @ExceptionHandler(Exception.class)
    String unexpected(Exception e, Model model, HttpServletRequest req, HttpServletResponse res) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), e);
        return errorPage(model, req, res, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Something went wrong on our side. Please try again.", null);
    }

    private static String friendly(ApiException e) {
        return switch (e.status()) {
            case 403 -> "You do not have permission to do that.";
            case 404 -> "The record you asked for was not found.";
            default -> e.getMessage();
        };
    }

    /** @ModelAttribute data is not applied to exception-handler views, so the layout's data is set here too. */
    private String errorPage(Model model, HttpServletRequest req, HttpServletResponse res, int status,
                             String detail, JsonNode errors) {
        res.setStatus(status);
        model.addAttribute("user", session);
        model.addAttribute("currentPath", req.getRequestURI());
        model.addAttribute("status", status);
        model.addAttribute("detail", detail);
        model.addAttribute("errors", errors);
        return "error-page";
    }
}
