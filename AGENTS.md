# Repository Guidelines

## Project Context

BringMyCart 2.0 is a centralized saved-product and rewards platform. MVP supports Amazon India product links only; it never reads Amazon credentials, modifies retailer carts, or processes checkout. Affiliate-funded rewards are conditional on verified policy permission and usable attribution. Core saved-product functionality must remain independent of rewards.

## Confirmed Stack and Organization

Use a Java 21/Spring Boot modular monolith with one shared backend, Maven, PostgreSQL through Spring Data JPA/Hibernate, a React.js + Vite customer/admin web application, Spring Security with JWT-based authentication, and a Chrome Manifest V3 extension. The exact currently supported Spring Boot version remains TBD pending verification; hosting and detailed authentication/token design also remain TBD. Keep domain modules cohesive (for example `identity`, `catalog`, `saved-products`, `rewards`, `gift-cards`, `admin`) with clear internal APIs; do not introduce microservices without approval. Place backend, web, extension, tests, and documentation in clearly named top-level directories once approved. `docs/SRS.md` is the source of product requirements; `docs/ARCHITECTURE.md` is the proposed architecture pending approval.

## Documentation-First, Incremental Work

Do not implement application code until the SRS and initial architecture are approved. Before each increment, identify affected requirements and record assumptions, alternatives, and trade-offs. Never implement the whole product in one task, invent requirements, or add unrelated features. Ask before major architectural decisions; favor simple, maintainable conventions over speculative abstractions. Report files changed and tests actually run, and never claim verification that was not performed.

## Backend, API, and Data Guidelines

Use conventional Spring Boot layering and constructor injection. Prefer `PascalCase` types, `camelCase` members/methods, and four-space Java indentation; use formatter/linter configuration once selected. Design REST endpoints around resources, validated input, consistent errors, authorization, pagination where relevant, and idempotency for state-changing operations that can be retried. Do not trust client authentication state.

Model financial-like state through an append-only, auditable reward ledger. Ensure transaction boundaries, concurrency safety, idempotent redemption, and recoverable partial failures before changing points or inventory. PostgreSQL and Spring Data JPA/Hibernate are confirmed; data rules must prevent duplicate product saves where applicable, double point spending, and double gift-card assignment.

## Frontend and Extension Guidelines

Use React function components, small focused modules, `PascalCase` component files, and `camelCase` hooks/utilities. The React website and extension share one BringMyCart account system; keep customer and admin routes separated and enforce authentication and authorization server-side. The specific authentication mechanism remains TBD. The extension must use Manifest V3, operate only on supported Amazon India product pages, prompt unauthenticated users to sign in, and use a secure backend-validated return flow (TBD). Never access Amazon credentials or alter Amazon carts.

## Security, Rewards, and External Integrations

Never hardcode or log secrets, tokens, or gift-card codes. Gift-card codes require authenticated encryption, external key management, and least-privilege access; never expose them to unauthorized users or unnecessary systems. After a successful redemption, a code may be securely returned only to its rightful owner and operationally necessary authorized administrators. Do not expose codes through general APIs, logs, browser storage, errors, or analytics. Audit sensitive admin, inventory, and redemption actions.

Treat affiliate APIs, product data, link generation, commission reporting, and incentive permissions as unverified until documented by authorized sources. Product information may include ASIN, title, original URL, image, and price only where legally and technically permitted; no field is guaranteed to be available. Do not assume customer-level attribution, webhooks, commission sharing, or reward eligibility. Gift-card redemption workflows may be tested with controlled test balances and inventory, but real affiliate-funded reward issuance remains blocked pending policy and attribution verification. Use documented integrations only, preserve original product URLs, disclose affiliate relationships as required, and report blockers rather than silently changing the business model.

## Windows Development Guardrails

Use the configured Java 21 JDK for Maven and IntelliJ; do not rely on a different system-default JDK. Keep Docker/Testcontainers compatibility settings test-scoped rather than setting global `JAVA_TOOL_OPTIONS`, Docker Engine options, or user-wide Docker overrides. If a Windows/JDBC timezone issue requires `-Duser.timezone=Asia/Kolkata`, apply it only to the affected local run configuration and document the reason; do not commit credentials or a universal timezone assumption.

## Testing, Git, and Traceability

Add meaningful automated tests for implemented behavior, including authorization, validation, duplicate handling, transaction failures, retries, and concurrency-sensitive flows. Run the project’s configured test, lint, and build commands before review; no commands are configured yet. Use focused imperative commits, such as `feat(rewards): add redemption validation`. Pull requests should state requirement IDs, testing performed, risks, linked issues, and UI evidence when relevant. Keep every implementation change traceable to `docs/SRS.md` requirements.
