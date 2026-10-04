package com.rto.config;

import com.rto.core.Public;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenApiConfig {
    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI rtoOpenApi() {
        Schema<?> error = new Schema<>().type("object")
                .addProperty("detail", new Schema<>().type("string"))
                .addProperty("code", new Schema<>().type("string"))
                .addProperty("errors", new Schema<>().type("array").items(new Schema<>().type("object")));
        return new OpenAPI()
                .info(new Info().title("RTO Management API").version("1.0.0").description("""
                        Backend for the RTO Management System; the MySQL schema in `database/` is the source of truth.

                        * Authenticate with `POST /api/v1/auth/login`, then send `Authorization: Bearer <access_token>`.
                        * Every endpoint enforces database-driven RBAC (`user_roles` + `role_permissions`).
                        * Errors are always `{"detail": "...", "code": "MACHINE_CODE"}` (400/401/403/404/409/422).
                        * Collections return `{items, page, page_size, total, pages}`."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSchemas("ErrorResponse", error))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Documents the uniform error contract on every operation; public endpoints drop the bearer requirement. */
    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handler) -> {
            Content content = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            Map.of("400", "Bad request", "401", "Not authenticated", "403", "Permission denied", "404", "Not found",
                    "409", "Conflict (duplicate, invalid state transition, capacity...)", "422", "Validation error")
                    .forEach((code, desc) -> operation.getResponses().addApiResponse(code, new ApiResponse().description(desc).content(content)));
            if (handler.hasMethodAnnotation(Public.class)) operation.setSecurity(java.util.List.of());
            return operation;
        };
    }
}
