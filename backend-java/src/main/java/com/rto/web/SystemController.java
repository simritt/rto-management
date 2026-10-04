package com.rto.web;

import com.rto.core.Db;
import com.rto.core.Public;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "system")
public class SystemController {
    private final Db db;

    public SystemController(Db db) {
        this.db = db;
    }

    @Public
    @GetMapping("/health")
    @Operation(summary = "Liveness + database connectivity")
    public Map<String, Object> health() {
        Number tables = (Number) db.em().createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")
                .getSingleResult();
        return Map.of("status", "ok", "database", "up", "tables", tables.intValue());
    }
}
