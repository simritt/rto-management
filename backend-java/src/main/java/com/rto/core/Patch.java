package com.rto.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PATCH bodies must distinguish "field absent" from "field set to null" (e.g. clearing validity_period_days).
 * The raw JSON is parsed into the DTO, validated like any other request, and presence is queryable by JSON name.
 */
@Component
public class Patch {
    private final ObjectMapper mapper;
    private final Validator validator;

    public Patch(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public record Parsed<T>(T dto, JsonNode node) {
        /** true when the client sent the field at all (even as null). */
        public boolean has(String jsonName) {
            return node.has(jsonName);
        }
    }

    public <T> Parsed<T> parse(JsonNode body, Class<T> type) {
        if (body == null || !body.isObject()) {
            throw ApiException.unprocessable("Request validation failed",
                    List.of(Map.of("field", "body", "message", "a JSON object is required")));
        }
        T dto;
        try {
            dto = mapper.treeToValue(body, type);
        } catch (Exception e) {
            throw ApiException.unprocessable("Request validation failed",
                    List.of(Map.of("field", "body", "message", "Malformed or unsupported request body")));
        }
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            List<Map<String, String>> errs = new ArrayList<>();
            for (ConstraintViolation<T> v : violations) {
                String path = v.getPropertyPath().toString();
                errs.add(Map.of("field", path.isEmpty() ? "body" : Names.snakePath(path), "message", v.getMessage()));
            }
            throw ApiException.unprocessable("Request validation failed", errs);
        }
        return new Parsed<>(dto, body);
    }
}
