# BringMyCart 2.0

BringMyCart is a centralized saved-product and rewards platform. The current implementation provides the identity foundation, Amazon India, Flipkart, and Myntra saved-product workflows, a React customer dashboard, and a Chrome MV3 extension with PKCE-based account authorization and in-page product saving. Amazon metadata is enriched asynchronously through Axesso RapidAPI; Flipkart and Myntra metadata uses the backend-only Scrape.do adapter with validated extension metadata fallback. Affiliate links and rewards remain blocked pending verification; Amazon review-summary display remains blocked by provider usage-permission confirmation.

## Confirmed stack

- Java 21 (Temurin `21.0.13` for local Windows development)
- Spring Boot `3.5.6`, Maven Wrapper, PostgreSQL 17, Spring Data JPA/Hibernate
- Spring Boot Actuator, JUnit 5, and Testcontainers PostgreSQL
- React.js + TypeScript + Vite customer dashboard; Chrome Manifest V3 TypeScript extension

Spring Boot 3.5.6 is the approved provisional version. Verify the current supported Spring Boot 3.x patch online before any upgrade; do not upgrade to Spring Boot 4.x without approval.

## Repository structure

```text
backend/
  src/main/java/com/bringmycart/bmc/   Spring Boot bootstrap and feature modules
    retailer/                          retailer URL validation and Scrape.do adapter
    savedproducts/                     saved-product API, persistence and enrichment
  src/main/resources/                  shared/dev config and Flyway migrations
  src/test/                            Testcontainers PostgreSQL integration tests
docs/                                  SRS, architecture, and phase reports
frontend/                              React + TypeScript + Vite customer dashboard
```

The backend follows the approved feature-first modular-monolith boundaries: `identity`, `savedproducts`, `retailer`, `affiliate`, `rewards`, `giftcards`, and `adminaudit`. Identity and saved-products contain implemented types; unapproved business modules remain intentionally absent.

## PostgreSQL development database

Use the existing container; do not create or reset another database:

```cmd
docker start bringmycart-postgres
```

The `dev` profile connects to `127.0.0.1:5432/bringmycart_dev`. It requires `DB_USERNAME` and `DB_PASSWORD`; Hibernate uses `ddl-auto: none`, so Phase 1 does not create or change business tables.

### Windows CMD

```cmd
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "DB_USERNAME=bmc_user"
set "DB_PASSWORD=<your local password>"
```

### PowerShell

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:DB_USERNAME = "bmc_user"
$env:DB_PASSWORD = "<your local password>"
```

Never put the password in a committed file or a shared terminal history.

## Build, test, and run

Run these from the repository root in CMD:

```cmd
backend\mvnw.cmd -f backend\pom.xml clean verify
backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

In PowerShell, use the same commands with `./` syntax:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml clean verify
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

In IntelliJ IDEA, open `backend/pom.xml`, select the Java 21 JDK above for the project and Maven runner, configure `DB_USERNAME` and `DB_PASSWORD` in the Spring Boot run configuration, then run `com.bringmycart.bmc.BringMyCartApplication`.

## Health verification

After startup, verify the only exposed Actuator endpoint:

```cmd
curl http://localhost:8080/actuator/health
```

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

The endpoint should return HTTP 200 when the application is healthy. Do not infer database-specific detail unless the response body or logs have been inspected.

## Windows, Docker, and timezone troubleshooting

The integration test starts its own disposable PostgreSQL 17 container. It never uses or alters `bringmycart_dev`.

- Docker Engine 29 requires Docker API 1.44 or later. The Maven Surefire configuration supplies `-Dapi.version=1.44` only to test JVMs. Do not configure a lower global `DOCKER_API_VERSION`, a global `JAVA_TOOL_OPTIONS`, or an obsolete `DOCKER_HOST`.
- Re-run the Testcontainers suite locally after dependency resolution to reconfirm the scoped setting with Docker Desktop; no Docker configuration needs to be changed.
- A Windows/JDBC startup error can occur if the JVM supplies `Asia/Calcutta`, which PostgreSQL rejects. The verified local workaround is `-Duser.timezone=Asia/Kolkata`. Apply it only to the affected CMD session or IntelliJ run configuration; do not make it a universal application or deployment default.

For the temporary CMD-only timezone workaround:

```cmd
set "JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Kolkata"
backend\mvnw.cmd -f backend\pom.xml spring-boot:run
set "JAVA_TOOL_OPTIONS="
```

The official Chrome Web Store URL, authentication design, reward rules, and affiliate activation remain unresolved or blocked as documented in `docs/SRS.md` and `docs/ARCHITECTURE.md`.

## Phase 2 identity configuration

Phase 2 adds registration, login, RS256 access tokens, refresh-token rotation, logout, RBAC, Flyway migrations, and authentication rate limiting. Supply JWT keys and settings through local environment variables; never commit PEM files or values.

### CMD example

```cmd
set "JWT_PRIVATE_KEY_LOCATION=file:C:/secure-local/bmc-private.pem"
set "JWT_PUBLIC_KEY_LOCATION=file:C:/secure-local/bmc-public.pem"
set "JWT_ISSUER=bringmycart"
set "JWT_AUDIENCE=bringmycart-api"
set "JWT_REFRESH_COOKIE_SECURE=false"
set "CORS_ALLOWED_ORIGINS=http://localhost:5173"
```

### Generate local development keys without OpenSSL

The standalone Java 21 utility at `backend/tools/GenerateLocalJwtKeys.java` uses only JDK APIs. From the repository root, run it in Java source-file mode:

```cmd
java backend\tools\GenerateLocalJwtKeys.java
```

It creates a 3072-bit RSA PKCS#8 private key at `backend/.local-keys/jwt-private.pem` and an X.509 SubjectPublicKeyInfo public key at `backend/.local-keys/jwt-public.pem`. It refuses to overwrite either file and never prints key material. `.local-keys/` is Git-ignored.

Set the key locations to the corresponding absolute `file:` paths in your local environment, for example:

```cmd
set "JWT_PRIVATE_KEY_LOCATION=file:C:/path/to/BringMyCart/backend/.local-keys/jwt-private.pem"
set "JWT_PUBLIC_KEY_LOCATION=file:C:/path/to/BringMyCart/backend/.local-keys/jwt-public.pem"
```

Remove the two PEM files when they are no longer needed; keep the utility only while local key generation remains useful.

`JWT_REFRESH_COOKIE_SECURE=false` is for local HTTP development only. Production must use HTTPS and a secure cookie. The test profile generates an ephemeral in-memory RSA key pair; it does not use or commit a private key.

Flyway owns the identity schema. `V1__create_identity_tables.sql` creates `users` and `refresh_tokens`; Hibernate remains `ddl-auto: none`. Public registration can only create `CUSTOMER` accounts. Provision the first `ADMIN` through an approved, audited out-of-band operational procedure that generates a BCrypt hash; no seed account or public administrator-registration endpoint exists.

### Authentication endpoints

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/auth/register` | Register a CUSTOMER account. |
| `POST /api/v1/auth/login` | Return a 15-minute access token and set an HttpOnly refresh cookie. |
| `POST /api/v1/auth/refresh` | Rotate the 7-day refresh token; requires CSRF protection for browser use. |
| `POST /api/v1/auth/logout` | Revoke the current refresh token and clear its cookie. |
| `GET /api/v1/auth/csrf` | Initialize the readable CSRF cookie before a browser cookie-authenticated POST. |
| `GET /api/v1/auth/me` | Verify bearer-token authentication. |
| `GET /api/v1/identity/admin/ping` | Minimal ADMIN-only authorization verification endpoint. |

Access tokens remain valid until their expiration. Logout revokes refresh tokens only; it does not falsely claim immediate stateless access-token revocation. Refresh tokens are random, stored only as SHA-256 hashes, rotated atomically, and their token family is revoked when a rotated token is replayed.

For browser clients, refresh tokens use `HttpOnly`, `SameSite=Strict` cookies scoped to `/api/v1/auth`; CSRF remains enabled for refresh and logout. The React bootstrap first requests `GET /api/v1/auth/csrf`, then submits the cookie/header double-submit pair for refresh. Login and registration are CSRF-exempt because they do not rely on an existing authentication cookie. Future extension authentication must use the architecture's registered callback, PKCE, state/nonce, one-time authorization-code flow—not page scripts or URL tokens.

Developer verification before the final targeted transaction-hardening change completed with all 15 tests passing:

```cmd
backend\mvnw.cmd -f backend\pom.xml clean verify
```

## Saved-product enrichment and manual refresh

Saving or manually refreshing a product returns before Axesso RapidAPI is contacted. The backend persists a `PENDING` record, then submits bounded background work only after the save or refresh transaction commits. The backend sends `GET https://axesso-axesso-amazon-data-service-v1.p.rapidapi.com/amz/amazon-lookup-product?url=...` with the Amazon India canonical product URL encoded in the `url` query parameter, `x-rapidapi-key`, and `x-rapidapi-host`. Set `AXESSO_RAPIDAPI_KEY` only in the backend environment; the key and raw provider errors are never stored or returned. The default connection/read timeouts are 5/30 seconds.

```cmd
set "AXESSO_RAPIDAPI_KEY=<local key>"
set "AXESSO_RAPIDAPI_HOST=axesso-axesso-amazon-data-service-v1.p.rapidapi.com"
set "AXESSO_MONTHLY_REQUEST_LIMIT=100"
```

The configured RapidAPI BASIC plan provides a 100-request monthly allowance at ₹0. The application enforces a configurable 100-request monthly cap per running backend process. This guard resets on process restart and is not shared between multiple application instances; reconcile usage against the RapidAPI account dashboard before restarting/deploying additional instances. RapidAPI's usage and billing dashboard remains authoritative, and the application cannot prevent provider-side overage charges if other clients use the same subscription.

`POST /api/v1/saved-products/{id}/refresh` is owner-scoped, returns `202 Accepted`, and has a five-minute default per-product cooldown (`PRODUCT_ENRICHMENT_MANUAL_REFRESH_COOLDOWN`). It preserves the last successful metadata until a replacement fetch succeeds. A `PENDING` or `PROCESSING` record returns a conflict rather than spending another provider credit.

Crash and executor-saturation recovery is disabled by default. To enable conservative recovery of stale or eligible transiently failed jobs, set `PRODUCT_ENRICHMENT_RECOVERY_ENABLED=true`; its default interval is 15 minutes, stale threshold is 30 minutes, and batch size is 10. It never refreshes `COMPLETED` products and is not price monitoring. A restart can therefore leave a product `PENDING` or `PROCESSING` until this explicit recovery option is enabled. Automated provider retries remain bounded by `PRODUCT_ENRICHMENT_MAX_ATTEMPTS` (default `2`) and apply only to timeout, rate-limit, and temporary provider failures.

Amazon review summaries and sentiment topics are not persisted until Axesso's applicable storage and usage terms are verified. Automated tests use mocked provider responses and never consume RapidAPI quota. For a local metadata check, configure the backend key, save an Amazon India product, then inspect its existing saved-product response after enrichment; do not use repeated saves or refreshes to probe the free quota.

### Flipkart and Myntra metadata (Phase 5A)

The existing saved-products API accepts validated HTTPS product URLs from `amazon.in`, `flipkart.com`, and `myntra.com`. Flipkart identity uses the `pid` query parameter when present (otherwise the product path ID); Myntra identity uses the numeric product ID in a product path. Duplicate identity is scoped to `(user, retailer, retailer product ID)`. The original URL is retained for Buy navigation; tracking parameters are removed from the canonical identity URL. The forward-only Flyway `V8` migration adds retailer identity and optional metadata fields while backfilling existing rows as Amazon/ASIN records.

For Flipkart and Myntra only, the asynchronous enrichment worker makes one Scrape.do API-mode request to `https://api.scrape.do/`, using the required `token` and URL-encoded target `url` query parameters. Set the backend-only `SCRAPEDO_API_TOKEN`; optional settings are `SCRAPEDO_ENABLED` (default `true`), `SCRAPEDO_TIMEOUT` (default `20s`, bounded to 1–60 seconds), and `SCRAPEDO_MAX_RESPONSE_BYTES` (default 2,000,000, bounded to 5 MB). The request disables Scrape.do retry and target redirects and does not enable JavaScript rendering, residential proxies, or additional bypass options. The provider token is required in the outbound Scrape.do query per its API contract; it must never be logged or exposed to clients. Scrape.do's current product/credit pricing and account-specific terms should be verified before making live requests. No automated test makes a live request.

The backend parses only `application/ld+json`, Open Graph, standard metadata, and a small retailer-selector fallback with Jsoup. Malformed JSON-LD is skipped. Response data is bounded and treated as untrusted text; URLs must already have passed the retailer allowlist. A CAPTCHA/access-denied/interstitial page is not stored as product data. If provider data is incomplete, validated extension fields fill missing values; if the provider fails, a sufficient extension title remains saved as completed metadata. Otherwise, the URL-only saved product remains and enrichment is marked failed. Missing price stays null; unknown availability stays `UNKNOWN`. No periodic price tracking is added.

Automated verification commands from the repository root:

```cmd
backend\mvnw.cmd -f backend\pom.xml clean verify
cd frontend && npm.cmd run test && npm.cmd run build
cd ..\extension && npm.cmd run test && npm.cmd run typecheck && npm.cmd run build
```

Use mocked Scrape.do responses and sanitized HTML fixtures for tests. Do not configure a production token in the frontend or extension.

## React dashboard

The React + TypeScript + Vite dashboard is in `frontend/`. It retains access tokens only in memory and relies on the existing HttpOnly refresh cookie for session restoration. Axesso RapidAPI credentials never belong in frontend environment variables.

Use Node.js `24.15.0` or newer for the current frontend dependency set. Node `24.3.0` can install packages but reports engine warnings from `jsdom` and `w3c-xmlserializer`; do not suppress those warnings.

```cmd
cd frontend
copy .env.example .env.local
npm.cmd install
npm.cmd run dev
```

Set the backend's existing CORS allowlist before starting the Spring Boot application locally:

```cmd
set "CORS_ALLOWED_ORIGINS=http://localhost:5173"
```

The default `VITE_API_BASE_URL` is `http://localhost:8080/api/v1`. For browser-cookie development over local HTTP, use the existing backend development setting `JWT_REFRESH_COOKIE_SECURE=false`; production requires HTTPS and a secure refresh cookie. The frontend sends `X-XSRF-TOKEN` from the browser's `XSRF-TOKEN` cookie on state-changing requests. Before its first cookie-authenticated refresh, it calls `GET /api/v1/auth/csrf`; the readable CSRF cookie is not an access credential.

Run frontend checks after dependencies install:

```cmd
npm.cmd run test
npm.cmd run build
```

### Amazon reviews status

Phase 3 displays the provider-observed aggregate rating and review count when available. The database has reserved JSONB fields for future review-summary and topic data, but the enrichment pipeline deliberately does **not** persist provider review summaries or topics. The Amazon Reviews popup/modal and review-summary display are pending verification of account-specific Axesso metadata storage and usage permissions. Until that verification is recorded, the dashboard must show neither fabricated review content nor stored provider summaries.
# Phase 4 extension account synchronization

For local website-to-extension lifecycle signals, set `VITE_BMC_EXTENSION_ID` in `frontend/.env.local` to the loaded development extension ID. The example file contains the current local ID; verify it at `chrome://extensions` after each unpacked reinstall. The extension manifest currently allows external messages only from `http://localhost:5173`. Production web origins and extension IDs must be explicitly registered before release. Lifecycle messages contain no credentials or identity claims; the extension still requires its existing PKCE authorization and validates the account through the backend.
