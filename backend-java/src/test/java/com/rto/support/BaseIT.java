package com.rto.support;

import com.rto.cli.AdminCli;
import com.rto.cli.BootstrapService;
import com.rto.cli.SchemaApplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.Map;

/**
 * Base for every integration test. All tests run against a REAL MySQL (row locks, generated columns and CHECK
 * constraints cannot be faked). The throw-away database `TEST_DB_NAME` is dropped and rebuilt ONCE per JVM from the
 * unmodified database/*.sql files; it can never be the real DB_NAME. Credentials come from environment variables or
 * backend-java/.env (DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, TEST_DB_NAME).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestBeans.class)
public abstract class BaseIT {
    public static final String JWT_SECRET = "test-only-secret-" + "x".repeat(40);

    private static final Map<String, String> ENV = AdminCli.loadEnv(Path.of(".env"));
    static final String HOST = ENV.getOrDefault("DB_HOST", "127.0.0.1");
    static final String PORT = ENV.getOrDefault("DB_PORT", "3306");
    static final String USER = ENV.getOrDefault("DB_USER", "");
    static final String PASSWORD = ENV.getOrDefault("DB_PASSWORD", "");
    static final String TEST_DB = ENV.getOrDefault("TEST_DB_NAME", "rto_management_test");

    static {
        if (USER.isBlank()) throw new IllegalStateException("Set DB_USER/DB_PASSWORD (and DB_HOST/DB_PORT) in backend-java/.env or the environment before running tests.");
        if (TEST_DB.equals(ENV.getOrDefault("DB_NAME", "rto_management"))) throw new IllegalStateException("TEST_DB_NAME must differ from DB_NAME");
        try {
            SchemaApplier.apply(AdminCli.jdbcServerUrl(HOST, PORT), USER, PASSWORD, TEST_DB, true);   // fresh schema, once per JVM
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:mysql://" + HOST + ":" + PORT + "/" + TEST_DB
                + "?useUnicode=true&characterEncoding=utf8&yearIsDateType=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=PREFERRED");
        r.add("spring.datasource.username", () -> USER);
        r.add("spring.datasource.password", () -> PASSWORD);
        r.add("rto.jwt-secret", () -> JWT_SECRET);
        r.add("rto.storage-dir", () -> "target/test-storage");
    }

    private static volatile boolean seeded;

    @Autowired private BootstrapService bootstrap;
    @Autowired protected Api api;
    @Autowired protected Fx fx;
    @Autowired protected Flows flows;

    @BeforeEach
    void seedOnce() {
        if (!seeded) {
            synchronized (BaseIT.class) {
                if (!seeded) {
                    bootstrap.seedRbac();
                    bootstrap.seedPayableTypes();
                    seeded = true;
                }
            }
        }
    }
}
