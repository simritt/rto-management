package com.rto.cli;

import com.rto.RtoApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * One-shot administration commands that run BEFORE Spring starts (they must work while the application's own
 * database credentials do not exist yet):
 *
 *   --rto.command=setup-mysql   create the rto_app user + database, apply database/*.sql, seed, write .env
 *   --rto.command=apply-schema  apply database/*.sql to DB_NAME (or --test: recreate TEST_DB_NAME)
 */
public final class AdminCli {
    private AdminCli() {}

    private static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    static String random(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        return sb.toString();
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> o = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) continue;
            String body = a.substring(2);
            int eq = body.indexOf('=');
            if (eq >= 0) o.put(body.substring(0, eq), body.substring(eq + 1));
            else if (i + 1 < args.length && !args[i + 1].startsWith("--")) o.put(body, args[++i]);
            else o.put(body, "true");
        }
        return o;
    }

    /** Minimal KEY=VALUE reader for backend-java/.env; real environment variables win. */
    public static Map<String, String> loadEnv(Path file) {
        Map<String, String> env = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String l = line.strip();
                    if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) continue;
                    env.put(l.substring(0, l.indexOf('=')).strip(), l.substring(l.indexOf('=') + 1).strip());
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        System.getenv().forEach(env::put);
        return env;
    }

    public static String jdbcServerUrl(String host, String port) {
        return "jdbc:mysql://" + host + ":" + port + "/?allowPublicKeyRetrieval=true&sslMode=PREFERRED&useUnicode=true&characterEncoding=utf8";
    }

    // ---- apply-schema ---------------------------------------------------------------------------------------

    public static int applySchema(String[] args) throws Exception {
        Map<String, String> o = options(args), env = loadEnv(Path.of(o.getOrDefault("env-file", ".env")));
        boolean test = o.containsKey("test");
        String db = test ? env.getOrDefault("TEST_DB_NAME", "rto_management_test") : env.getOrDefault("DB_NAME", "rto_management");
        String url = jdbcServerUrl(env.getOrDefault("DB_HOST", "127.0.0.1"), env.getOrDefault("DB_PORT", "3306"));
        int n = SchemaApplier.apply(url, env.getOrDefault("DB_USER", ""), env.getOrDefault("DB_PASSWORD", ""), db, test);
        System.out.println((test ? "Recreated " : "Created ") + db + " (" + n + " statements)");
        return 0;
    }

    // ---- setup-mysql ----------------------------------------------------------------------------------------

    public static int setupMysql(String[] args) throws Exception {
        Map<String, String> o = options(args);
        String host = o.getOrDefault("host", "127.0.0.1"), port = o.getOrDefault("port", "3307");
        String adminUser = o.getOrDefault("admin-user", "root");
        String db = o.getOrDefault("db-name", "rto_management"), testDb = o.getOrDefault("test-db-name", "rto_management_test");
        Path envFile = Path.of(o.getOrDefault("env-file", ".env"));
        for (String n : new String[]{db, testDb}) {
            if (!n.matches("[A-Za-z0-9_]+")) {
                System.out.println("Database names may contain only letters, digits and underscores.");
                return 2;
            }
        }
        String adminPw = System.getenv("MYSQL_ADMIN_PASSWORD");
        if (adminPw == null) {
            Console console = System.console();
            if (console == null) {
                System.out.println("No interactive console. Run this from a terminal, or set MYSQL_ADMIN_PASSWORD for this one command.");
                return 2;
            }
            adminPw = new String(console.readPassword("MySQL password for %s@%s:%s (hidden): ", adminUser, host, port));
        }

        String appPw = random(28), adminLoginPw = random(20), jwt = random(64);
        String serverUrl = jdbcServerUrl(host, port);
        try (Connection c = DriverManager.getConnection(serverUrl, adminUser, adminPw); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT VERSION()")) {
                rs.next();
                System.out.println("Connected to MySQL " + rs.getString(1));
            }
            for (String h : new String[]{"localhost", "127.0.0.1"}) {
                st.execute("CREATE USER IF NOT EXISTS 'rto_app'@'" + h + "' IDENTIFIED BY '" + appPw + "'");
                st.execute("ALTER USER 'rto_app'@'" + h + "' IDENTIFIED BY '" + appPw + "'");
                for (String d : new String[]{db, testDb}) st.execute("GRANT ALL ON `" + d + "`.* TO 'rto_app'@'" + h + "'");
            }
            System.out.println("User rto_app created with access limited to " + db + " and " + testDb);
            st.execute("CREATE DATABASE IF NOT EXISTS `" + testDb + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            System.out.println("\nCould not connect/administer MySQL: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("Check the host/port/password and try again.");
            return 1;
        }

        // The schema is applied with the ADMIN account (CREATE DATABASE needs it); the app itself only ever uses rto_app.
        int exists;
        try (Connection c = DriverManager.getConnection(serverUrl, adminUser, adminPw); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '" + db + "'")) {
            rs.next();
            exists = rs.getInt(1);
        }
        if (exists > 0) {
            System.out.println(db + " already has " + exists + " tables; schema left untouched.");
        } else {
            int n = SchemaApplier.apply(serverUrl, adminUser, adminPw, db, false);
            System.out.println("Applied database/*.sql to " + db + " (" + n + " statements)");
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("DB_HOST", host);
        values.put("DB_PORT", port);
        values.put("DB_USER", "rto_app");
        values.put("DB_PASSWORD", appPw);
        values.put("DB_NAME", db);
        values.put("TEST_DB_NAME", testDb);
        values.put("JWT_SECRET", jwt);
        values.put("BOOTSTRAP_ADMIN_USERNAME", "admin");
        values.put("BOOTSTRAP_ADMIN_PASSWORD", adminLoginPw);
        String template = Files.isRegularFile(Path.of(".env.example")) ? Files.readString(Path.of(".env.example"), StandardCharsets.UTF_8) : "";
        for (var e : values.entrySet()) {
            template = template.replaceAll("(?m)^" + e.getKey() + "=.*$", Matcher.quoteReplacement(e.getKey() + "=" + e.getValue()));
            if (!template.contains(e.getKey() + "=")) template += "\n" + e.getKey() + "=" + e.getValue();
        }
        if (Files.exists(envFile)) {
            Files.move(envFile, envFile.resolveSibling(envFile.getFileName() + ".backup"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Existing " + envFile.getFileName() + " moved to " + envFile.getFileName() + ".backup");
        }
        Files.writeString(envFile, template, StandardCharsets.UTF_8);
        System.out.println("Wrote " + envFile.toAbsolutePath());

        // Seed roles/permissions/payable types and create the admin, using exactly what was just written.
        values.forEach(System::setProperty);
        SpringApplication app = new SpringApplication(RtoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        String[] boot = o.containsKey("sample-data")
                ? new String[]{"--rto.command=bootstrap", "--sample-data"} : new String[]{"--rto.command=bootstrap"};
        app.run(boot).close();

        System.out.println("\n=== DONE ===");
        System.out.println("Admin login for the API:  username: admin   password: " + adminLoginPw);
        System.out.println("Start the API:   run.cmd   (or ./run.sh)     then open http://127.0.0.1:8000/docs");
        return 0;
    }
}
