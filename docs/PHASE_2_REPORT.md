# Phase 2 Report — Identity & Authentication

**Status:** Complete; developer verification passed before the final targeted transaction-hardening change.  
**Branch:** `feature/phase-2-auth`  
**Scope:** Identity & Access only; no Phase 3+ features are included.

## Implemented Endpoints

| Endpoint | Behavior |
| --- | --- |
| `POST /api/v1/auth/register` | Validates and normalizes email, enforces password rules, stores BCrypt hash, and creates only `CUSTOMER` users. |
| `POST /api/v1/auth/login` | Authenticates credentials, returns an RS256 access token, and sets an HttpOnly refresh cookie. |
| `POST /api/v1/auth/refresh` | Accepts only the refresh cookie plus a valid CSRF cookie/header pair, rotates the token in one transaction, and revokes the family on replay. |
| `POST /api/v1/auth/logout` | Requires the same CSRF cookie/header protection, revokes the current refresh token, and clears its cookie. |
| `GET /api/v1/auth/me` | Minimal authenticated JWT verification endpoint. |
| `GET /api/v1/identity/admin/ping` | Minimal ADMIN-only authorization verification endpoint. |

## Persistence and Migrations

Flyway migration `V1__create_identity_tables.sql` creates:

- `users`: UUID, normalized unique email, BCrypt hash, CUSTOMER/ADMIN role, enabled state, and timestamps.
- `refresh_tokens`: UUID, user foreign key, SHA-256 token hash, token family, rotation reference, expiry, revocation time, and timestamps.

No raw refresh token, seed administrator, password, or JWT key is persisted. Hibernate remains non-mutating (`ddl-auto: none`). The initial administrator must be provisioned through an approved, audited process using a BCrypt hash; public registration cannot select an ADMIN role.

## Security Design

- Access tokens use RS256, issuer, audience, subject/user ID, role, issued/expiry times, and JWT ID claims.
- Runtime public/private PEM locations and security settings are externalized.
- Refresh tokens are 64-byte cryptographically random values. Only SHA-256 hashes are stored.
- Rotation locks the presented token row. Reuse of a rotated token revokes active members of its family; this deliberately treats concurrent refreshes as suspicious.
- A refresh-specific rejection exception is the only exception exempted from rollback. This commits intended token/family revocation for replay, expiry, or disabled-account rejection; unexpected failures and unrelated API exceptions still roll back.
- Browser refresh tokens use `HttpOnly`, `SameSite=Strict`, scoped cookies. Secure cookies are enabled by default and disabled only for local HTTP through external configuration.
- CSRF remains enabled for refresh/logout. `CookieCsrfTokenRepository` issues an `XSRF-TOKEN` cookie and the plain request handler validates its raw value against the `X-XSRF-TOKEN` header. Login and registration are specifically exempt because they do not rely on an existing refresh cookie.
- Authentication responses use generic credential errors. Validation, conflict, unauthorized, forbidden, and rate-limit responses are structured and avoid stack traces or sensitive values.
- Login/registration limiting is bounded in-memory storage for a single instance. It is not a distributed limiter.

## Extension Compatibility

This phase does not implement the Chrome extension handoff. The future design remains an authorization-code-style flow using registered callback origins, PKCE, state, nonce, user binding, short expiry, one-time code exchange, and replay prevention. Tokens are never intended for arbitrary page scripts or URL query strings.

## Configuration

Required development environment values include `DB_USERNAME`, `DB_PASSWORD`, `JWT_PRIVATE_KEY_LOCATION`, and `JWT_PUBLIC_KEY_LOCATION`. Optional externalized settings cover issuer, audience, token lifetimes, cookie security, allowed CORS origins, and rate limits. See the root README for CMD and PowerShell examples. Never commit PEM files, `.env` files, passwords, or tokens.

## Tests Added

`AuthIntegrationTest` uses Testcontainers PostgreSQL and covers registration, validation, duplicate-email handling, BCrypt storage, login, invalid/disabled credentials, valid/invalid/expired JWT access, CUSTOMER/ADMIN authorization, authenticated `/api/v1/auth/me`, CSRF cookie/header success, missing/mismatched CSRF rejection, refresh rotation, persisted replay-family revocation, concurrent access, logout revocation, rate limits, Flyway table creation, and PostgreSQL persistence constraints. The Phase 1 health test is retained and uses an ephemeral test RSA key pair.

## Verification Status

Developer verification before the final targeted transaction-hardening review change, on Windows 11 with Java 21, Docker Desktop, PostgreSQL 17, and the `test` Testcontainers profile, completed successfully:

- `mvnw.cmd clean verify`: **15 tests run, 15 passed, 0 failures, 0 errors**.
- Manual Postman checks confirmed registration, login, JWT-protected `/api/v1/auth/me`, refresh rotation with CSRF protection, logout (`204`), and revoked-refresh-token rejection (`401`).

Run from a Windows CMD session with Docker Desktop access:

```cmd
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
backend\mvnw.cmd -f backend\pom.xml clean verify
```

Then set DB/JWT environment variables, start the existing PostgreSQL container, run `spring-boot:run`, and inspect `http://localhost:8080/actuator/health`. Do not infer database-specific health detail without inspecting the response.

## Known Limitations and Approval Decisions

1. Confirm the exact key-management and key-rotation operations before production deployment.
2. Confirm access/refresh lifetimes, issuer, audience, cookie domain, SameSite policy, and allowed CORS origins for each client environment.
3. Approve the first-administrator provisioning runbook; no public or seeded administrator account exists.
4. Replace the single-instance limiter with a shared strategy only when multi-instance deployment is approved.
5. Approve the Chrome extension callback and authorization-code handoff before implementing it.
6. Access-token logout is bounded by the 15-minute token lifetime; no access-token denylist is implemented.
7. The CSRF approach is a standard cookie-based double-submit pattern. It relies on HTTPS, host-only cookies, `SameSite=Strict`, and strict CORS, and it does not protect against XSS. Evaluate signed or server-bound CSRF tokens if a future threat model includes hostile subdomains or cookie-injection risks.
