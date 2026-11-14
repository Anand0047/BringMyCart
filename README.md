# BringMyCart 2.0

BringMyCart is a centralized saved-product and rewards platform. Phase 2 now provides the backend identity foundation: customer registration and login, JWT access tokens, refresh-token rotation/logout, CUSTOMER/ADMIN authorization, and Flyway-managed identity storage. Saved products, retailer integration, affiliate links, rewards, gift cards, frontend, and Chrome extension behavior remain unimplemented.

## Confirmed stack

- Java 21 (Temurin `21.0.13` for local Windows development)
- Spring Boot `3.5.6`, Maven Wrapper, PostgreSQL 17, Spring Data JPA/Hibernate
- Spring Boot Actuator, JUnit 5, and Testcontainers PostgreSQL
- Future clients: React.js + Vite and Chrome Manifest V3

Spring Boot 3.5.6 is the approved provisional version. Verify the current supported Spring Boot 3.x patch online before any upgrade; do not upgrade to Spring Boot 4.x without approval.

## Repository structure

```text
backend/
  src/main/java/com/bringmycart/bmc/   Spring Boot bootstrap and feature modules
  src/main/resources/                  shared/dev config and Flyway migrations
  src/test/                            Testcontainers PostgreSQL integration tests
docs/                                  SRS, architecture, and phase reports
```

The backend follows the approved feature-first modular-monolith boundaries: `identity`, `savedproducts`, `retailer`, `affiliate`, `rewards`, `giftcards`, and `adminaudit`. Business types are intentionally absent until their approved phases.

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
| `GET /api/v1/auth/me` | Verify bearer-token authentication. |
| `GET /api/v1/identity/admin/ping` | Minimal ADMIN-only authorization verification endpoint. |

Access tokens remain valid until their expiration. Logout revokes refresh tokens only; it does not falsely claim immediate stateless access-token revocation. Refresh tokens are random, stored only as SHA-256 hashes, rotated atomically, and their token family is revoked when a rotated token is replayed.

For browser clients, refresh tokens use `HttpOnly`, `SameSite=Strict` cookies scoped to `/api/v1/auth`; CSRF remains enabled for refresh and logout. Login and registration are CSRF-exempt because they do not rely on an existing authentication cookie. Future extension authentication must use the architecture's registered callback, PKCE, state/nonce, one-time authorization-code flow—not page scripts or URL tokens.

Developer verification before the final targeted transaction-hardening change completed with all 15 tests passing:

```cmd
backend\mvnw.cmd -f backend\pom.xml clean verify
```
