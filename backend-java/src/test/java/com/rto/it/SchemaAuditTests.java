package com.rto.it;

import com.rto.support.BaseIT;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Database coverage audit: the JPA entities must mirror the live schema built from the unmodified database/*.sql. */
class SchemaAuditTests extends BaseIT {
    static final int EXPECTED_TABLES = 63;

    @PersistenceContext EntityManager em;

    record Col(boolean nullable, boolean generated) {}

    private Map<String, Col> mappedColumns() {
        Map<String, Col> out = new HashMap<>();
        for (EntityType<?> et : em.getMetamodel().getEntities()) {
            Class<?> c = et.getJavaType();
            String table = c.getAnnotation(Table.class).name();
            for (Field f : c.getDeclaredFields()) {
                Column col = f.getAnnotation(Column.class);
                if (col == null) continue;
                boolean generated = !col.insertable() && !col.updatable() && !col.name().equals("updated_at");
                out.put(table + "." + col.name(), new Col(col.nullable() || f.isAnnotationPresent(Id.class), generated));
            }
        }
        return out;
    }

    @Test
    void sixtyThreeEntitiesAndSixtyThreeTables() {
        assertThat(em.getMetamodel().getEntities()).hasSize(EXPECTED_TABLES);
        assertThat(fx.count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")).isEqualTo(EXPECTED_TABLES);
    }

    @Test
    void everyTableAndColumnIsModelled() {
        Map<String, Col> db = new HashMap<>();
        for (Map<String, Object> r : fx.jdbc.queryForList("SELECT table_name, column_name, is_nullable, extra FROM information_schema.columns WHERE table_schema = DATABASE()")) {
            String extra = String.valueOf(r.get("EXTRA")).toUpperCase();
            // DEFAULT_GENERATED only means DEFAULT CURRENT_TIMESTAMP; real generated columns say STORED/VIRTUAL GENERATED
            db.put(r.get("TABLE_NAME") + "." + r.get("COLUMN_NAME"), new Col("YES".equals(r.get("IS_NULLABLE")), extra.contains("STORED GENERATED") || extra.contains("VIRTUAL GENERATED")));
        }
        Map<String, Col> mapped = mappedColumns();
        assertThat(new TreeSet<>(db.keySet())).as("columns in DB but missing from entities").isEqualTo(new TreeSet<>(mapped.keySet()));
        db.forEach((key, d) -> {
            assertThat(mapped.get(key).generated()).as("generated-column flag on " + key).isEqualTo(d.generated());
        });
    }

    @Test
    void nullabilityMatchesForNonKeyColumns() {
        Map<String, Col> mapped = mappedColumns();
        for (Map<String, Object> r : fx.jdbc.queryForList("SELECT table_name, column_name, is_nullable, column_key FROM information_schema.columns WHERE table_schema = DATABASE()")) {
            if ("PRI".equals(r.get("COLUMN_KEY"))) continue;
            String key = r.get("TABLE_NAME") + "." + r.get("COLUMN_NAME");
            assertThat(mapped.get(key).nullable()).as("nullability of " + key).isEqualTo("YES".equals(r.get("IS_NULLABLE")));
        }
    }

    @Test
    void allForeignKeysFromTheSqlFilesExistInTheDatabase() throws IOException {
        Pattern fk = Pattern.compile("FOREIGN KEY");
        long expected = 0;
        try (var files = Files.list(Path.of("../database"))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.getFileName().toString().matches("(0[1-9]|1[0-4])_.*\\.sql"))::iterator) {
                String sql = Files.readString(p);
                // count only real constraint clauses, not comments
                for (String line : sql.split("\n")) if (!line.strip().startsWith("--") && fk.matcher(line).find()) expected++;
            }
        }
        assertThat(fx.count("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND constraint_type = 'FOREIGN KEY'")).isEqualTo(expected);
    }

    @Test
    void checkConstraintsAndGeneratedColumnGuardsExist() {
        List<String> checks = fx.jdbc.queryForList("SELECT constraint_name FROM information_schema.check_constraints WHERE constraint_schema = DATABASE()", String.class);
        assertThat(checks).contains("chk_slot_capacity", "chk_learner_dates", "chk_dl_dates", "chk_fitness_dates", "chk_puc_dates", "chk_insurance_dates", "chk_permit_dates");
        Set<String> gen = new TreeSet<>(fx.jdbc.queryForList("SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND (extra LIKE '%STORED GENERATED%' OR extra LIKE '%VIRTUAL GENERATED%')", String.class));
        assertThat(gen).containsExactlyInAnyOrder("employee_postings.current_flag", "learner_licences.active_flag", "vehicle_ownerships.current_flag");
    }

    @Test
    void everyEntityHasATableAnnotation() {
        for (EntityType<?> et : em.getMetamodel().getEntities()) {
            assertThat(et.getJavaType().isAnnotationPresent(Entity.class)).isTrue();
            assertThat(et.getJavaType().isAnnotationPresent(Table.class)).isTrue();
        }
    }
}
