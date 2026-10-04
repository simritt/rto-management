package com.rto.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Arrays;
import java.util.List;

/** Typed view of the `rto.*` configuration (all values come from environment variables / backend-java/.env). */
@ConfigurationProperties(prefix = "rto")
public record Settings(
        @DefaultValue("") String jwtSecret,
        @DefaultValue("30") int accessTokenMinutes,
        @DefaultValue("7") int refreshTokenDays,
        @DefaultValue("http://localhost:5173,http://localhost:3000") String corsOrigins,
        @DefaultValue("./storage") String storageDir,
        @DefaultValue("10") int maxUploadMb,
        @DefaultValue("mock") String notificationProvider,
        @DefaultValue("") String paymentGatewayKey,
        @DefaultValue("admin") String bootstrapAdminUsername,
        @DefaultValue("") String bootstrapAdminPassword) {

    public List<String> corsOriginList() {
        return Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
