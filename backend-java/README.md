# RTO Management System – Java backend

Spring Boot 3 (Java 21) + JPA/Hibernate + MySQL 8 implementation of the RTO workflows on top of the schema in
[`../database`](../database). The SQL files are the **source of truth**: they are applied unmodified, nothing is
generated from the entities, and `SchemaAuditTests` fails if an entity drifts from the live schema.

It exposes **exactly the same HTTP contract** as the Python reference backend in [`../backend`](../backend)
(same URLs, snake_case JSON, error codes, pagination shape) - 225 operations over 151 paths. The reference
backend's test-suite was run against this server over HTTP as a parity gate (176 passed, 9 skipped because
those tests call Python internals); those 9 have Java-native equivalents here.

> **There is no frontend in the repository yet.** CORS is pre-configured for `http://localhost:5173` and
> `http://localhost:3000` (`CORS_ORIGINS`).

## Quick start (Windows or Linux/macOS)

You need MySQL 8+ running. You do **not** need to install Java or Maven globally: a portable JDK 21 and Maven
live in `.tools/` (git-ignored; see "Toolchain" below).

### 1. Connect MySQL - one command

```bat
setup-mysql.cmd --port=3307
```
(Linux/macOS: `./setup-mysql.sh --port=3307`. MySQL 8.0 on the dev machine is on port 3307; the default is 3307 too.)

You are asked for the MySQL admin password **once**, at a hidden prompt (it is never stored or printed). The
command then:

1. creates a least-privilege MySQL user `rto_app` (random password) limited to the two RTO databases,
2. builds `rto_management` from the unmodified `../database/*.sql` (skipped if it already has tables),
3. seeds roles, permissions and payable types and creates the first `admin` login,
4. writes `.env` (host, port, `rto_app` credentials, JWT secret) - an existing `.env` is moved to `.env.backup`,
5. prints the admin password **once** - save it.

Options: `--host`, `--port`, `--admin-user`, `--db-name`, `--test-db-name`, `--env-file`, `--sample-data`
(starter master data). `MYSQL_ADMIN_PASSWORD` may be set for non-interactive use.

Prefer manual setup? Copy `.env.example` to `.env`, fill it in, then run
`java -jar target/rto-management-backend-1.0.0.jar --rto.command=apply-schema` and
`... --rto.command=bootstrap`.

### 2. Run

```bat
run.cmd
```
* Swagger UI: <http://127.0.0.1:8000/docs> (**Authorize** with the token from `POST /api/v1/auth/login`)
* OpenAPI JSON: <http://127.0.0.1:8000/openapi.json> - Health: `GET /api/v1/health`

### 3. Test

```bat
mvn.cmd test
```
185 JUnit tests run against a **real MySQL** (row locks, generated columns and CHECK constraints cannot be faked).
The throw-away database `TEST_DB_NAME` is dropped and rebuilt once per run from the unmodified SQL files; it can
never be the real `DB_NAME`. The MySQL account in `.env` (or `DB_HOST/DB_PORT/DB_USER/DB_PASSWORD` environment
variables) needs `CREATE/DROP` on `rto_management_test` - the `rto_app` user from `setup-mysql` has exactly that.

### Frontend + backend together
No frontend exists yet. When one does, run it on port 5173 or 3000 with API base URL
`http://127.0.0.1:8000/api/v1`:
```bat
:: terminal 1
backend-java\run.cmd
:: terminal 2
cd frontend && npm install && npm run dev
```

## Toolchain
`mvn.cmd` / `mvn.sh` run the project-local Maven with the project-local JDK (`.tools/jdk-21*`,
`.tools/apache-maven-3.9.9`, dependencies cached in `.tools/m2`). If `.tools/` is missing, install JDK 21+ and
Maven 3.9+ yourself and run `mvn test` / `mvn package`; nothing else depends on the portable copy.

## Architecture
```
src/main/java/com/rto
  RtoApplication            entry point (+ admin commands that run before Spring: setup-mysql, apply-schema)
  domain/                   63 JPA entities, one per table (generated columns are read-only; password_hash is @JsonIgnore)
  core/                     Db (locking + queries) · QB (query builder) · errors · pagination · audit · storage
                            RBAC annotations (@Requires/@RequiresAny/@Public) + AuthInterceptor · Perms (seed catalogue)
  security/                 JWT (HS256, access+refresh, revocation) · bcrypt · Spring Security filter chain (CORS, stateless)
  service/                  ALL business rules and transactions (controllers stay thin)
  web/                      REST controllers        dto/   request/response records
  cli/                      schema applier · bootstrap seeding · setup-mysql
src/test/java/com/rto       support/ (MockMvc client, data factories, flow builders) + it/ (the test classes)
```
Conventions: errors are always `{"detail": "...", "code": "MACHINE_CODE"}` (400/401/403/404/409/422, validation adds
`errors[]`); collections return `{items, page, page_size, total, pages}` with `page_size <= 100` and whitelisted
`sort` keys; money is `BigDecimal` serialised as exact strings (`"1500.50"`); every multi-step operation is one
`@Transactional` unit (workflow change + history row + audit row commit or roll back together); timestamps are
UTC; MySQL error numbers are translated to business messages so SQL never reaches a client.

### Authentication & RBAC
`POST /auth/login|refresh|logout`, `GET /auth/me`. Permissions come **only** from `user_roles` ->
`role_permissions` -> `permissions`, re-read on every request (a role change applies instantly). Endpoints
declare `@Requires("application.approve")`; `ADMIN` is just a role holding every permission. Citizen-facing
resources scope by identity: `<x>.view` sees everything, `<x>.view_own` only the caller's own records.
Seeded roles: `ADMIN`, `RTO_OFFICER`, `COUNTER_CLERK`, `INSPECTOR`, `ENFORCEMENT_OFFICER`, `ACCOUNTANT`, `CITIZEN`.

### Where the important rules live
| Area | Rule (service) |
|---|---|
| Applications | explicit transition table (`ApplicationService.TRANSITIONS`), 409 otherwise; verified/unexpired documents required before appointment/payment; fee must be paid before APPROVED |
| Appointments | slot row locks in a fixed order + guarded `UPDATE ... WHERE booked_count < capacity`; table CHECK is the last defence |
| Ownership transfer | one transaction: lock transfer -> vehicle -> current owner, close old row, open new row, audit; any failure rolls back |
| Payments | polymorphic target validated against the real table, exact amount, idempotent `gateway_reference`, one success per item, target -> payment lock order, refunds reserved/completed transactionally |
| Appeals | target must exist and be appealable; the remedy (cancel challan / reopen application / reinstate licence) commits with the decision |
| Notifications | delivered AFTER commit in their own transactions through `NotificationProvider`; a failing provider marks the row FAILED and never affects the business operation |
| Audit | append-only (no write API); secrets and national IDs are redacted before writing |

## Known limitations (deliberate)
* **Token revocation is in-process** (the schema has no session table): logout/refresh-rotation revocations reset on
  restart and are not shared between instances. Back `JwtService.revoke` with Redis for multi-instance use.
* **Payments record gateway outcomes; they do not call a gateway.** Plug a real gateway in behind `PaymentService`
  (credentials via `PAYMENT_GATEWAY_KEY`).
* `uq_slot` includes the nullable `counter_id`; MySQL treats NULLs as distinct in UNIQUE keys, so counter-less
  duplicate slots are blocked by the service under an office row lock rather than by the database.
* Cross-payment reuse of a `gateway_reference` has no unique index in the schema; same-payment replays are fully
  race-safe, simultaneous reuse across *different* payments is best-effort.
* `payable_types` is read-only through the API: each name is wired to a target resolver in code.
* No scheduler: `POST /driving-licences/expire-overdue`, `/permits/expire-overdue` and
  `/compliance/refresh-statuses` are meant to be run daily by cron.
* The JDBC URL uses `allowPublicKeyRetrieval=true&sslMode=PREFERRED` for local MySQL 8 convenience; enable TLS
  (`sslMode=VERIFY_CA`) in production.
