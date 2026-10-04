package com.rto.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Applies the UNMODIFIED database/*.sql files to a MySQL server. The only substitution is the database name
 * (`rto_management` -> target) so the same files can also build the throw-away test database.
 */
public final class SchemaApplier {
    private SchemaApplier() {}

    static final List<String> FILES = List.of("01_create_database.sql", "02_reference_tables.sql", "03_identity.sql",
            "04_office_structure.sql", "05_employees.sql", "06_rbac.sql", "07_applications.sql", "08_licence.sql", "09_vehicles.sql",
            "10_compliance.sql", "11_permits.sql", "12_violations.sql", "13_payments.sql", "14_support.sql");
    // 00_run_all.sql uses the mysql-client-only SOURCE command; 99_verify.sql is read-only

    /** Finds the repository's database/ folder from the working directory (works from backend-java/ or the repo root). */
    public static Path findSqlDir() {
        for (String c : new String[]{"../database", "database", "../../database"}) {
            Path p = Path.of(c).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("01_create_database.sql"))) return p;
        }
        throw new IllegalStateException("Could not find the database/ folder (looked relative to " + Path.of("").toAbsolutePath() + ")");
    }

    /** Quote-aware splitter: ';' inside '...' (e.g. COMMENT strings) does not end a statement; '-- ' comments are dropped. */
    public static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        char quote = 0;
        int i = 0, n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (quote != 0) {
                buf.append(c);
                if (c == '\\' && i + 1 < n) {
                    buf.append(sql.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                buf.append(c);
            } else if (c == '-' && sql.startsWith("-- ", i)) {
                while (i < n && sql.charAt(i) != '\n') i++;
                continue;
            } else if (c == ';') {
                String stmt = buf.toString().strip();
                if (!stmt.isEmpty()) out.add(stmt);
                buf.setLength(0);
            } else {
                buf.append(c);
            }
            i++;
        }
        String tail = buf.toString().strip();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    /** @param url a JDBC URL WITHOUT a default database, e.g. jdbc:mysql://127.0.0.1:3307/ */
    public static int apply(String url, String user, String password, String dbName, boolean recreate) throws SQLException, IOException {
        if (!dbName.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("unsafe database name");
        Path dir = findSqlDir();
        int count = 0;
        try (Connection c = DriverManager.getConnection(url, user, password); Statement st = c.createStatement()) {
            if (recreate) {
                st.execute("DROP DATABASE IF EXISTS `" + dbName + "`");
            } else {
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '" + dbName + "'")) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new IllegalStateException("Database " + dbName + " already contains " + rs.getInt(1) + " tables; refusing to modify it.");
                    }
                }
            }
            for (String file : FILES) {
                String sql = Files.readString(dir.resolve(file), StandardCharsets.UTF_8)
                        .replaceAll("\\brto_management\\b", Matcher.quoteReplacement(dbName));
                for (String stmt : split(sql)) {
                    st.execute(stmt);
                    count++;
                }
            }
        }
        return count;
    }
}
