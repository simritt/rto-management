package com.rto.web.api;

import com.fasterxml.jackson.databind.JsonNode;

/** An error response from the backend API: {"detail", "code", "errors"?}. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final JsonNode errors;

    public ApiException(int status, String code, String detail, JsonNode errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public JsonNode errors() {
        return errors;
    }
}
