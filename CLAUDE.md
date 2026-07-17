# CLAUDE.md — backend

Spring Boot REST API for the School Management App — a multi-tenant SaaS platform. Started single-tenant, converted to multi-tenant via a 6-phase plan (MT-1 through MT-6f, all shipped). Serves two separate client populations: tenant apps (`web`, `android` — one school per login) and the platform team's own tools (`operator` console, `marketing` public site). See `README.md` for local setup/run instructions — this file is about how the code is organized and the non-obvious things worth knowing before changing it.

**Sibling repos** (same backend, different clients): `school-management-app-ui` (web), `school-management-app-android`, `school-management-app-operator` (internal platform console), `school-management-app-marketing` (public site). None share code; each hand-mirrors this API's DTOs independently.

## Stack

Spring Boot 3.3.4, Java 21, Maven, PostgreSQL. `spring-boot-starter-{web,data-jpa,security,validation,mail,actuator}`, **Flyway** (`ddl-auto: validate` — schema changes go through migrations only, never Hibernate auto-DDL), **JJWT 0.12.6**, **springdoc-openapi 2.6.0** (Swagger UI `/swagger-ui.html`, spec `/v3/api-docs`), **OpenPDF** (report cards), **Apache Commons CSV** (bulk import), **Lombok**, **Testcontainers** + **Cucumber 7** for tests.

## Two identity surfaces on one schema

- **Tenant users** — `Role`: `ADMIN`/`TEACHER`/`PARENT`, scoped to one school (`schoolId`). No STUDENT login — students are records linked to a `PARENT` user. Login via `/auth/*`.
- **Platform users** — `PlatformRole`, not scoped to any school, sees across all tenants. Login via `/platform/auth/*` (optional TOTP MFA). `JwtAuthFilter` rejects a platform token on a tenant path and vice versa.

## Architecture

Package-by-feature under `com.school.app`: `analytics, attendance, auth, billing, event, examresult, fee (+ fee.payment), homework (+ homework.submission), leaverequest, library, messaging, notice, platform, student, timetable, transport, user`, plus `common/ { config, exception, notification, security, storage }`. Each feature package bundles its own `Controller`/`Service`/`Repository`/`Entity`/DTOs/`Mapper` (no MapStruct/ModelMapper, manual mapping).

- **`platform`** — the tenant-independent SaaS control plane: `PlatformUser`/`PlatformAuthController` (MFA via `TotpService`), school provisioning (`ProvisioningService`, `ActivationService`/`ActivationToken`), `SubscriptionPlan`/`Subscription`/`Entitlement`/`FeatureKey`/`PlanCode`/`PlanDefaults`, `EntitlementService` + `@RequiresEntitlement` aspect, `SignupRequest`/`PublicSignupController` (reviewed path) + `PublicTrialSignupController` (instant self-service), `AuditLog`/`AuditService`, `PlatformAnalyticsService`, `PlatformSettings` (operator-controlled toggles like `autoApproveSignups`), `CaptchaVerifier`/`TurnstileCaptchaVerifier`. Largest package in the codebase.
- **`billing`** — manual/offline billing (DD/Cheque/NEFT, not a payment gateway — see below): `PaymentClaim`, `BillingController`/`BillingService` (tenant-facing), `PlatformPaymentController`/`PlatformPaymentService` (operator verify/reject), `SubscriptionOverdueJob` (`@Scheduled`, the only automatic subscription-status transition — `ACTIVE → PAST_DUE`).
- **`common/security`** — `JwtService`, `JwtAuthFilter`, `UserDetailsServiceImpl`, plus tenancy plumbing: `TenantContext`, `SchoolTenantResolver` (Hibernate `CurrentTenantIdentifierResolver` — **read its Javadoc before writing any code that discovers its tenant mid-transaction**, it documents a real Hibernate gotcha), `TenantRlsTransactionListener`, `TenantSafeRepositoryImpl` (custom `repositoryBaseClass`, see below).

JPA entities are never returned from controllers — every endpoint returns a mapped DTO. Authorization is enforced with `@PreAuthorize` at the service/controller-method level, not just the filter chain; parent-scoped endpoints additionally check the requested student belongs to the authenticated parent.

## Multi-tenancy — read this before touching tenant data access

Tenant isolation works in three layers together; all three matter, none is sufficient alone:

1. **JPA-level:** every tenant entity has `@TenantId schoolId`; `SchoolTenantResolver` + `TenantContext` (thread-local, set from the JWT's `schoolId` claim in `JwtAuthFilter`) filter HQL/JPQL/Criteria queries automatically.
2. **Postgres RLS (V17):** `FORCE ROW LEVEL SECURITY` on every tenant table, policy `USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid)` — defense-in-depth if the JPA filter is ever bypassed. `TenantRlsTransactionListener` re-applies this session GUC on transaction start.
3. **`findById` fix:** Hibernate's `@TenantId` does **not** filter `EntityManager.find()` — what Spring Data JPA's `findById()` calls under the hood (documented Hibernate limitation, HHH-16179/HHH-16626). Fixed via a custom `repositoryBaseClass`, `TenantSafeRepositoryImpl`, routing `findById` through JPQL instead. **Any new entity needs `@TenantId`, and any hand-written native `@Query` needs its own `WHERE school_id = ?`** — native queries bypass `@TenantId` regardless.

**Known sharp edges (all hit in production, all fixed, still worth knowing):**
- `CurrentTenantIdentifierResolver` is consulted **once**, at Session/transaction start. Code that discovers its own tenant *mid-transaction* (provisioning, activation, login's pre-auth lookup) can't rely on setting `TenantContext` partway through to retroactively filter that Session's `@TenantId` population. Workaround: a native-query bypass method (`findByIdBypassingTenantFilter`, `insertBypassingTenantFilter`, `activateBypassingTenantFilter`, etc.) plus explicit `TenantRlsTransactionListener.applyCurrentTenant(entityManager)`.
- **Login's chicken-and-egg problem:** finding which tenant an email belongs to requires querying `users` *before* `TenantContext`/RLS has a tenant to filter by, but forced RLS hides every row with none set. Fixed (V25/V26, after a production login-lockout incident) via a narrow additional RLS policy (`pre_auth_login_lookup`) wrapped in an atomic Postgres function (`resolve_login_school_id`) so the set/read/reset sequence can't be split across JDBC statement boundaries by connection pooling.
- **Pooled-connection GUC gotcha (V27):** `current_setting('app.current_school_id', true)` reads `NULL` only the *first* time a session touches that GUC; a reused HikariCP connection reads an **empty string** instead, and `''::uuid` throws rather than safely denying. Fixed by wrapping every RLS policy's cast in `NULLIF(..., '')`.
- **RLS testing caveat:** Testcontainers'/docker-compose's default Postgres role is a superuser and silently bypasses RLS — tests that verify RLS must provision a genuinely restricted role.

Entitlement enforcement (separate layer on top): `EntitlementService` + `@RequiresEntitlement` aspect gate Payment/Conversation/Library/Analytics/Transport endpoints and notification dispatch; a suspended subscription short-circuits with `SUBSCRIPTION_SUSPENDED`. `GET /api/v1/subscription` is intentionally ADMIN-only — clients must default permissively for TEACHER/PARENT and let the server be the real gate.

Schema history: Flyway `V1`–`V27`. `V1`–`V14`: init → seed dev admin → `is_active` → search indexes → leave_requests → notifications → payments → homework_submissions → messaging → events → transport → library → `preferred_language` → book cover. `V15`: backfill teacher rows. `V16`–`V27` (MT plan + fixes): tenant isolation → RLS → platform subscriptions/entitlements → operator console → branding → self-service trial → billing-owner split → platform settings → manual billing → pre-auth login RLS policy → pre-auth login function → RLS empty-string fix. **Always check the actual latest migration on `master` before branching a new one** — parallel MT branches have needed manual V-number coordination before.

## REST API (base path `/api/v1` unless noted)

Core domain: `/auth`, `/users`, `/students`, `/attendance`, `/homework` (+submissions), `/fees`, `/payments` (Razorpay, per-fee only), `/exam-results`, `/leave-requests`, `/notices`, `/timetable`, `/events`, `/conversations`, `/library`, `/transport`, `/analytics`, `/notification-preferences`.

Platform/SaaS layer: `/subscription` (own plan+entitlements, ADMIN), `/branding` (every role reads, ADMIN+entitlement writes), `/billing` (payment instructions, report-a-payment, claim history, ADMIN), `/data-export` (ZIP of CSVs, ADMIN), `/users/{id}/billing-owner` (PATCH, billing-owner-only), `/public/signup-requests` + `/public/trial-signups` (unauthenticated, rate-limited + CAPTCHA), `/platform/auth/*` + `/platform/*` (operator-only, separate JWT — signup queue, schools, usage, payments, analytics, audit log, settings), `/activate/*` (public, token-based founding-admin activation).

## Auth

Stateless JWT. `JwtService` issues access (15m) and refresh (7d) tokens, HMAC-signed, `type` claim distinguishes them. Tenant tokens carry `schoolId`; platform tokens carry a platform role — `JwtAuthFilter` rejects cross-surface use. `SecurityConfig`: CSRF disabled, CORS via `CorsConfig` (default allow-list `localhost:5173/5174/5175`), BCrypt, `@EnableMethodSecurity` + `@PreAuthorize` everywhere. Public paths: `/auth/login`, `/auth/refresh`, `/platform/auth/login`, `/platform/auth/refresh`, `/public/signup-requests`, `/public/trial-signups`, `/activate/*`, Swagger, `/actuator/health`, `/payments/webhook` (HMAC instead), `POST /transport/routes/*/location` (per-route token header, GPS devices). Suspended-subscription 403s carry code `SUBSCRIPTION_SUSPENDED`; `PAST_DUE` adds an `X-Subscription-Status: PAST_DUE` response header instead (both consumed by client-side banners).

## i18n

`MessagesConfig` wires `ResourceBundleMessageSource` (`messages`/`messages_hi`) + `AcceptHeaderLocaleResolver` (en/hi, default en) — covers Bean Validation message-key overrides and `GlobalExceptionHandler`'s generic error text. Users also persist `preferredLanguage` (`PATCH /users/me/language`). Tested by `I18nIntegrationTest`.

## Cross-cutting patterns

- **Errors:** `GlobalExceptionHandler` → uniform `ErrorResponse`. `ResourceNotFoundException`→404, `BadRequestException`→400, `DuplicateResourceException`→409, `NotConfiguredException`→503, `BadCredentialsException`→401, `AccessDeniedException`→403, `MethodArgumentNotValidException`→400 with field errors.
- **DTOs:** every domain has `XDto` + `XCreateRequest`/`XUpdateRequest` + a manual `XMapper`.
- **Provider abstractions:** Email/SMS/Push, `PaymentGatewayProvider` (Razorpay, per-fee flow only — **not** subscription billing, see below), `FileStorageService`, `CaptchaVerifier` — all throw `NotConfiguredException`/503 gracefully when unconfigured, **except** `TurnstileCaptchaVerifier`, which deliberately passes every token through (loud warning) when unconfigured — a conscious "dev-ready" exception.
- **Soft delete:** Student, Notice, Timetable use `archive`/`restore` instead of hard deletes.
- **Subscription billing is manual/offline, not a gateway:** the school self-reports a payment (`POST /billing/payments`), an operator verifies (`PATCH /platform/payments/{id}/verify`, extends `currentPeriodEnd`) or rejects it. `SubscriptionOverdueJob` auto-flips `ACTIVE→PAST_DUE` on a lapsed period; `PAST_DUE→SUSPENDED` is always a manual operator call. A real gateway integration (webhooks, e-mandates, GST invoicing) remains a legitimate future phase, blocked on KYC.

## Testing

JUnit5 + Spring Boot Test; integration tests extend `AbstractIntegrationTest` (shared Testcontainers Postgres singleton + `TestRestTemplate`). Broad per-domain coverage plus multi-tenancy/platform-specific suites (`CrossTenantIsolationIntegrationTest`, `RowLevelSecurityIntegrationTest`, `EntitlementIntegrationTest`, `OperatorProvisioningIntegrationTest`, `PublicSignupIntegrationTest`, `SelfServiceTrialIntegrationTest`, `UsageMeteringIntegrationTest`, `ManualBillingIntegrationTest`, `DataExportIntegrationTest`, `BillingOwnerIntegrationTest`, `PlatformSettingsIntegrationTest`, `BrandingIntegrationTest`). Unit tests for pure logic and services throughout. Cucumber BDD suite (`cucumber` package) via `RunCucumberTest`. ~84 test files, full suite green (`mvn test` → 410/410 passing as of 2026-07-17).

**Test-writing gotcha:** `AbstractIntegrationTest`'s `@BeforeEach` sets the JUnit thread's `TenantContext` to the default seeded school; a subclass `@BeforeEach` that seeds its own test school must flip `TenantContext` to that school during seeding, then reset it back — meaning any **direct repository call** made later in a `@Test` method (after an HTTP round-trip, which sets `TenantContext` on a *different* server thread) needs `TenantContext.set(school.getId())` set again explicitly first, or `@TenantId`-filtered lookups silently return empty against the wrong tenant. See `EntitlementIntegrationTest`'s pattern.

## Deployment

- **Dockerfile:** multi-stage — `maven:3.9-eclipse-temurin-21` builds (tests skipped in-image, Testcontainers needs Docker-in-Docker), copied into `eclipse-temurin:21-jre-alpine`. `ENTRYPOINT` carries JVM flags added 2026-07-17 after Render deploys started timing out: `-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=75.0` (cold-start speed on Render's throttled free-tier CPU) and **`-Djava.security.egd=file:/dev/./urandom`** — the actual fix. Without it, startup hung indefinitely on this Alpine image's low system entropy, right as `BCryptPasswordEncoder`'s `SecureRandom` construction blocked during Spring Security wiring. 100% reproducible on Render, never reproduced locally (plenty of host entropy there) — verify any future startup-hang fix via a real Render deploy, not just a local run. If a deploy times out again, this exact playbook won't help twice; look for a different bottleneck.
- **render.yaml:** reference-only; the live service was provisioned manually (Docker runtime, health check `/actuator/health`, free plan). `SPRING_PROFILES_ACTIVE=prod` confirmed set 2026-07-17 (it had been silently missing before, so the service ran on `dev`'s DEBUG-level SQL logging until fixed).
- `server.port` resolves `PORT` (Render-injected) → `SERVER_PORT` → 8080.
- `docker-compose.yml` is local-dev-only (Postgres 16-alpine).

## Known gaps

- `NotificationPreferenceController` is ADMIN-only for `GET`/`PATCH /notification-preferences` — looks like it should be self-service per-user.
- Notification/payment provider credentials (email/SMS/push, Razorpay) are unset in production — degrade to 503 gracefully.
- Android has no FCM device-token registration endpoint — push is topic-based only, not per-device.
- Tenant data export ships the export half only — no legally-reviewed retention/deletion policy for cancelled tenants (needs DPDP legal review).
- Online payment gateway for subscription billing is unbuilt by design (see above).
