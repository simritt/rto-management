package com.rto.core;

import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One error contract for the whole API: {"detail": "...", "code": "MACHINE_CODE", "errors": [...optional]}. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, Object>> body(int status, String detail, String code, List<Map<String, String>> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("detail", detail);
        m.put("code", code);
        if (errors != null && !errors.isEmpty()) m.put("errors", errors);
        return ResponseEntity.status(status).body(m);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.status(), e.getMessage(), e.code(), e.errors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        List<Map<String, String>> errs = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errs.add(
                Map.of("field", Names.snakePath(f.getField()), "message", String.valueOf(f.getDefaultMessage()))));
        e.getBindingResult().getGlobalErrors().forEach(g -> errs.add(
                Map.of("field", "body", "message", String.valueOf(g.getDefaultMessage()))));
        return body(422, "Request validation failed", "VALIDATION_ERROR", errs);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> constraint(ConstraintViolationException e) {
        List<Map<String, String>> errs = new ArrayList<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            String path = v.getPropertyPath().toString();
            errs.add(Map.of("field", Names.snakePath(path.substring(path.lastIndexOf('.') + 1)), "message", v.getMessage()));
        }
        return body(422, "Request validation failed", "VALIDATION_ERROR", errs);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> methodValidation(HandlerMethodValidationException e) {
        List<Map<String, String>> errs = new ArrayList<>();
        e.getAllValidationResults().forEach(r -> r.getResolvableErrors().forEach(re -> errs.add(Map.of(
                "field", r.getMethodParameter().getParameterName() == null ? "param" : Names.snake(r.getMethodParameter().getParameterName()),
                "message", String.valueOf(re.getDefaultMessage())))));
        return body(422, "Request validation failed", "VALIDATION_ERROR", errs);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return body(422, "Request validation failed", "VALIDATION_ERROR",
                List.of(Map.of("field", "body", "message", "Malformed or unsupported request body")));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<Map<String, Object>> param(Exception e) {
        String field = e instanceof MethodArgumentTypeMismatchException m ? m.getName()
                : e instanceof MissingServletRequestParameterException p ? p.getParameterName()
                : ((MissingServletRequestPartException) e).getRequestPartName();
        return body(422, "Request validation failed", "VALIDATION_ERROR",
                List.of(Map.of("field", Names.snake(field), "message", "missing or invalid")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(400, "The uploaded file is too large", "FILE_TOO_LARGE", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
        return body(405, "Method Not Allowed", "METHOD_NOT_ALLOWED", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e) {
        return body(404, "Not Found", "NOT_FOUND", null);
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class, TransactionException.class,
            java.sql.SQLException.class})
    ResponseEntity<Map<String, Object>> db(Exception e) {
        ApiException translated = DbErrors.translate(e);
        if (translated != null) return body(translated.status(), translated.getMessage(), translated.code(), null);
        log.error("Unhandled database error", e);
        return body(500, "Internal server error", "INTERNAL_ERROR", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> other(Exception e) {
        ApiException translated = DbErrors.translate(e);   // e.g. a SQL error surfaced by a flush inside a service
        if (translated != null) return body(translated.status(), translated.getMessage(), translated.code(), null);
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of("detail", "Internal server error", "code", "INTERNAL_ERROR"));
    }
}
