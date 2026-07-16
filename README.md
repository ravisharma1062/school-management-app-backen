# School App — Backend

Spring Boot 3 / Java 21 REST API for the School Management App. Started as a single-tenant
school administration system (auth, student directory, attendance, timetable, homework, exam
results, notices, fees, library, transport, messaging, events, leave requests) and has since
been converted into a **multi-tenant SaaS platform**: every tenant-owned entity is isolated by
`schoolId` (JPA `@TenantId` + Postgres Row-Level Security), and a separate `platform`/`billing`
layer serves the operator console and marketing site — subscription plans/entitlements, school
provisioning, audit logging, and manual (DD/Cheque/NEFT) billing. See the top-level
[`PROJECT_KNOWLEDGE_BASE.md`](../PROJECT_KNOWLEDGE_BASE.md) for the full architecture writeup;
this README only covers running and developing this repo.

Four clients consume this API: `school-management-app-ui` (web, tenant users),
`school-management-app-android` (mobile, tenant users), `school-management-app-operator`
(internal platform-team console), and `school-management-app-marketing` (public site, no login).

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
   password `school_app` (see `docker-compose.yml`). Or run `./start-local.sh`, which does this
   step and the next one together.

2. Run the app:
   ```
   mvn spring-boot:run
   ```
   Flyway runs automatically on startup (`src/main/resources/db/migration`, currently up to
   `V27`), creating all tables and seeding two dev-only accounts:
   - Tenant admin (`V2__seed_dev_admin.sql`): `admin@school.app` / `Admin@123` — sign in via
     `/api/v1/auth/login`, used by the web/Android apps.
   - Platform admin (`V19__platform_operator_console.sql`): `operator@school.app` /
     `Operator@123`, no MFA enrolled — sign in via `/api/v1/platform/auth/login`, used by the
     operator console.

   **Change or remove these seeds before deploying anywhere but a local machine.**

3. API docs: once running, Swagger UI is at `http://localhost:8080/swagger-ui.html` and the
   raw OpenAPI JSON at `http://localhost:8080/v3/api-docs`. A snapshot is committed at
   `api-docs/openapi.json` — re-export it after changing any endpoint, since the web/Android/
   operator/marketing clients hand-mirror this contract with no shared codegen.

4. CORS defaults to allowing the four local dev clients: `localhost:5173` (web), `:5174`
   (operator), `:5175` (marketing) — see `common/config/CorsConfig.java`. Android talks to the
   backend directly (no CORS involved).

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
| `CAPTCHA_TURNSTILE_SECRET_KEY` | (unset) | Cloudflare Turnstile secret for the public signup/trial endpoints — unset means every CAPTCHA token is accepted (dev-ready, not production-safe) |
| `OPERATOR_ACTIVATION_BASE_URL` | `http://localhost:5174/activate` | Where a newly-provisioned school's founding-admin activation link points |
| `PLATFORM_TEAM_EMAIL` | `platform-team@school.app` | Notified when the public signup form gets a new submission |
| `MSG91_AUTH_KEY` / `SES_SMTP_*` / `RAZORPAY_KEY_*` | (unset) | Notification/payment provider credentials — unset means those integrations throw `NotConfiguredException` (503) rather than crash |

## Running tests

```
mvn test
```

- Unit tests (`JwtServiceTest`, `AuthServiceTest`, `AttendanceServiceTest`, `GradeCalculatorTest`,
  `FineCalculatorTest`, `AtRiskThresholdsTest`, `RazorpayProviderTest`, `LocalFileStorageServiceTest`,
  and others) run with plain JUnit/Mockito — no Docker required.
- Integration tests (`*IntegrationTest`) spin up a real Postgres container via Testcontainers —
  Docker must be running. Broad per-domain coverage plus multi-tenancy-specific suites:
  `CrossTenantIsolationIntegrationTest`, `RowLevelSecurityIntegrationTest`,
  `EntitlementIntegrationTest`, `OperatorProvisioningIntegrationTest`,
  `PublicSignupIntegrationTest`, `SelfServiceTrialIntegrationTest`, `UsageMeteringIntegrationTest`,
  `ManualBillingIntegrationTest`, `DataExportIntegrationTest`, `BillingOwnerIntegrationTest`.
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
  `notice/`, `fee/`, `library/`, `transport/`, `messaging/`, `event/`, `leaverequest/`,
  `analytics/`, `auth/`, `user/`), plus two tenant-independent packages added by the
  multi-tenant conversion: `platform/` (subscriptions, entitlements, provisioning, audit log,
  platform-team auth/MFA, public signup) and `billing/` (manual DD/Cheque/NEFT payment claims).
  Cross-cutting concerns live under `common/`.
- JPA entities are never returned from controllers — every endpoint returns a mapped DTO.
- `spring.jpa.hibernate.ddl-auto=validate` always — schema changes go through Flyway only.
- Authorization is enforced with `@PreAuthorize` at the service/controller-method level, not
  just via the security filter chain; parent-scoped endpoints additionally check that the
  requested student belongs to the authenticated parent.
- **Multi-tenancy:** every tenant-owned entity carries `@TenantId schoolId`, filtered
  automatically by Hibernate for HQL/JPQL/Criteria queries and backed by Postgres Row-Level
  Security as a defense-in-depth layer. `Repository.findById()` is routed through a custom
  `TenantSafeRepositoryImpl` because Hibernate's `@TenantId` does **not** filter
  `EntityManager.find()` (a documented Hibernate limitation) — any hand-written native `@Query`
  must add its own `WHERE school_id = ?` manually, since native queries bypass `@TenantId`
  regardless. See `common/security/SchoolTenantResolver`'s Javadoc before writing any code that
  discovers its own tenant mid-transaction (provisioning/activation/login all need this, and it
  has a real sharp edge — Hibernate's tenant resolver is consulted once, at Session start).
- Push notifications for new notices/homework go through
  `common/notification/PushNotificationService`, which currently logs the event. Swap its
  method bodies for real Firebase Cloud Messaging calls once FCM project credentials exist.

## Known gaps (tracked for follow-up, not blocking)

- `NotificationPreferenceController` is ADMIN-only for `GET`/`PATCH /notification-preferences`,
  which looks like it should be self-service per-user — worth revisiting.
- Notification/payment provider credentials (email/SMS/push, Razorpay for the per-fee flow) are
  unset in production; those integrations degrade to `NotConfiguredException`/503 rather than
  crash. Subscription billing itself does not use a payment gateway at all — it's manual/offline
  (DD/Cheque/NEFT, see `billing/`) by deliberate design, not a gap; a real gateway integration
  (webhooks, e-mandates, GST invoicing) remains a legitimate future phase.
- Android has no FCM device-token registration endpoint — server-side push is topic-based only
  (`notices-all`, `notices-<role>`), not per-device.
- Tenant data export (`GET /api/v1/data-export`) ships the export half of MT-6d only — a
  legally-reviewed retention/deletion policy for cancelled tenants (relevant given minors' data)
  has not been built, pending DPDP legal review.
- The full "one success + one denial per endpoint" role matrix is covered for the
  security-sensitive paths (student/attendance/exam-result/fee access by role, parent
  same-child checks) but not exhaustively duplicated for every single route — extend
  `StudentListAndRoleMatrixIntegrationTest` if stricter per-route proof is required before
  sign-off.

See [`PROJECT_KNOWLEDGE_BASE.md`](../PROJECT_KNOWLEDGE_BASE.md) §9 for the complete, cross-repo
list of known gaps.
