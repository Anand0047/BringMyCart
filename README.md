# BringMyCart 2.0

BringMyCart is a centralized saved-product and rewards platform. Phase 1 provides the backend foundation only: no accounts, JWTs, business entities, migrations, product-saving, rewards, gift cards, frontend, or Chrome extension behavior exists yet.

## Confirmed stack

- Java 21 (Temurin `21.0.13` for local Windows development)
- Spring Boot `3.5.6`, Maven Wrapper, PostgreSQL 17, Spring Data JPA/Hibernate
- Spring Boot Actuator, JUnit 5, and Testcontainers PostgreSQL
- Future clients: React.js + Vite and Chrome Manifest V3

Spring Boot 3.5.6 is the approved provisional version. Verify the current supported Spring Boot 3.x patch online before any upgrade; do not upgrade to Spring Boot 4.x without approval.

## Repository structure

```text
backend/
  src/main/java/com/bringmycart/bmc/   Spring Boot bootstrap and module markers
  src/main/resources/                  shared and dev configuration
  src/test/                            Testcontainers PostgreSQL health test
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

- Docker Engine 29 requires Docker API 1.44 or later. The build currently supplies `DOCKER_API_VERSION=1.44` only to the Surefire test JVM. Do not configure a lower global `DOCKER_API_VERSION` or an obsolete `DOCKER_HOST`.
- The developer-verified JVM workaround for Testcontainers is `-Dapi.version=1.44`. Prefer a Surefire-scoped system-property configuration over a global `JAVA_TOOL_OPTIONS` setting; confirm that change in the local Docker environment before replacing the existing test-scoped setting.
- A Windows/JDBC startup error can occur if the JVM supplies `Asia/Calcutta`, which PostgreSQL rejects. The verified local workaround is `-Duser.timezone=Asia/Kolkata`. Apply it only to the affected CMD session or IntelliJ run configuration; do not make it a universal application or deployment default.

For the temporary CMD-only timezone workaround:

```cmd
set "JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Kolkata"
backend\mvnw.cmd -f backend\pom.xml spring-boot:run
set "JAVA_TOOL_OPTIONS="
```

The official Chrome Web Store URL, authentication design, reward rules, and affiliate activation remain unresolved or blocked as documented in `docs/SRS.md` and `docs/ARCHITECTURE.md`.
