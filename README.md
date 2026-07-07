# School App — Backend

Spring Boot 3 / Java 21 REST API for the School Management App: auth, student directory,
attendance, timetable, homework, exam results, notices, and fee status.

## Prerequisites

- JDK 21+
- Maven 3.9+
- Docker (for local Postgres, and for running the Testcontainers-backed integration tests)

## Local setup

1. Start Postgres:
   ```
   docker compose up -d
   ```
   This starts Postgres 16 on `localhost:5432` with database `school_app` / user `school_app` /
   password `school_app` (see `docker-compose.yml`).

2. Run the app:
   ```
   mvn spring-boot:run
   ```
   Flyway runs automatically on startup (`src/main/resources/db/migration`), creating all
   tables and seeding one dev-only admin user:
   - email: `admin@school.app`
   - password: `Admin@123`

   **Change or remove this seed (`V2__seed_dev_admin.sql`) before deploying anywhere but a
   local machine.**

3. API docs: once running, Swagger UI is at `http://localhost:8080/swagger-ui.html` and the
   raw OpenAPI JSON at `http://localhost:8080/v3/api-docs`. Export the JSON from that endpoint
   and commit it (e.g. to `openapi.json`) once the backend is stable, as the contract the web
   and Android phases build against.

## Configuration

All config is externalized via environment variables (see `application.yml`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/school_app` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `school_app` / `school_app` | DB credentials |
| `JWT_SECRET` | (dev placeholder) | HMAC signing key — **must** be overridden outside local dev |
| `JWT_ACCESS_EXPIRATION_MS` | `900000` (15 min) | Access token lifetime |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 days) | Refresh token lifetime |
| `SERVER_PORT` | `8080` | HTTP port |

## Running tests

```
mvn test
```

- Unit tests (`JwtServiceTest`, `AuthServiceTest`, `AttendanceServiceTest`, `GradeCalculatorTest`)
  run with plain JUnit/Mockito — no Docker required.
- Integration tests (`*IntegrationTest`) spin up a real Postgres container via Testcontainers —
  Docker must be running.
- Cucumber acceptance tests (`RunCucumberTest`) drive the whole REST API end-to-end against a
  single Testcontainers Postgres. Feature files live in `src/test/resources/features/` (one per
  module) and step definitions in `com.school.app.cucumber`. Run just these with:
  ```
  mvn -Dtest=RunCucumberTest test
  ```
  A readable HTML report is written to `target/cucumber-report.html`.

> **Note:** the Surefire config pins `-Duser.timezone=UTC` (some hosts default to the legacy
> `Asia/Calcutta` zone id, which Postgres 16 rejects on connect) and the Docker Engine API version
> (`-Ddocker.api.version`, default `1.43`) so Testcontainers can talk to very new local daemons.
> Override the Docker API version with `-Ddocker.api.version=<value>` if your daemon needs it.

## Architecture notes

- Package-by-feature (`student/`, `attendance/`, `timetable/`, `homework/`, `examresult/`,
  `notice/`, `fee/`, `auth/`, `user/`), with cross-cutting concerns under `common/`.
- JPA entities are never returned from controllers — every endpoint returns a mapped DTO.
- `spring.jpa.hibernate.ddl-auto=validate` always — schema changes go through Flyway only.
- Authorization is enforced with `@PreAuthorize` at the service/controller-method level, not
  just via the security filter chain; parent-scoped endpoints additionally check that the
  requested student belongs to the authenticated parent.
- Push notifications for new notices/homework go through
  `common/notification/PushNotificationService`, which currently logs the event. Swap its
  method bodies for real Firebase Cloud Messaging calls once FCM project credentials exist —
  see section 12 of the top-level development plan (deployment/FCM decisions are deferred).

## Known gaps (tracked for follow-up, not blocking)

- There is no `POST /users` (or teacher-provisioning) endpoint in the v1 API spec — teacher and
  parent `User`/`Teacher` rows must currently be created directly against the database (or via
  a future admin user-management endpoint). The admin account is seeded via Flyway for this
  reason.
- The full "one success + one denial per endpoint" role matrix is covered for the
  security-sensitive paths (student/attendance/exam-result/fee access by role, parent
  same-child checks) but not exhaustively duplicated for every single route — extend
  `StudentListAndRoleMatrixIntegrationTest` if stricter per-route proof is required before
  sign-off.
