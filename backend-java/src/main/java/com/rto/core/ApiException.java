package com.rto.core;

import java.util.List;
import java.util.Map;

/**
 * Every business/validation failure. Rendered as {"detail": message, "code": CODE[, "errors": [...]]}.
 * It is a RuntimeException, so a @Transactional service rolls back everything when one escapes.
 */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<Map<String, String>> errors;

    public ApiException(int status, String code, String message, List<Map<String, String>> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public int status() { return status; }
    public String code() { return code; }
    public List<Map<String, String>> errors() { return errors; }

    public static ApiException badRequest(String code, String msg) { return new ApiException(400, code, msg, null); }
    public static ApiException unauthorized(String code, String msg) { return new ApiException(401, code, msg, null); }
    public static ApiException forbidden(String code, String msg) { return new ApiException(403, code, msg, null); }
    public static ApiException notFound(String msg) { return new ApiException(404, "NOT_FOUND", msg, null); }
    public static ApiException notFound(String code, String msg) { return new ApiException(404, code, msg, null); }
    public static ApiException conflict(String code, String msg) { return new ApiException(409, code, msg, null); }
    public static ApiException unprocessable(String msg, List<Map<String, String>> errors) {
        return new ApiException(422, "VALIDATION_ERROR", msg, errors);
    }
    public static ApiException internal() { return new ApiException(500, "INTERNAL_ERROR", "Internal server error", null); }
}
