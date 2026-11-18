# Phase 5A — Multi-Retailer Product Saving

**Status:** Implementation present; backend verification is pending. Frontend and extension checks are being recorded from the current local run. This report does not claim Phase 5A acceptance until the complete backend suite succeeds in a normal developer environment.

## Objective and scope

Add Flipkart and Myntra product saving to the existing BringMyCart flow while keeping Amazon India and Axesso unchanged. The implementation reuses the existing saved-products API, authenticated ownership, asynchronous enrichment, React dashboard, and Chrome MV3 extension. It does not add affiliate links, checkout, price monitoring, other retailers, or new infrastructure.

The SRS previously restricted its MVP to Amazon India. The product owner authorized the Phase 5A expansion; `docs/SRS.md` version 0.3 records that scope change while retaining existing FR identifiers and other requirements.

## Architecture and API

- `retailer` owns strict retailer URL validation and Scrape.do integration.
- `savedproducts` continues to own save/list/delete/refresh, persistence, and enrichment orchestration.
- Amazon records still use the existing Axesso provider. Flipkart and Myntra use Scrape.do.
- Existing authenticated saved-product endpoints and response shape are extended compatibly with retailer identity and optional metadata. Save remains user-triggered and enrichment remains asynchronous.
- Extension-supplied fields are optional, validated fallback input; backend URL parsing and authenticated identity remain authoritative.
- The dashboard displays retailer identity and opens the retained original product URL.

## Retailer identity and persistence

`RetailerProductUrlValidator` accepts HTTPS URLs only for exact supported hosts: Amazon India, Flipkart, and Myntra. It normalizes each product to a canonical host/path and rejects spoofed domains, invalid product IDs, and unsupported pages. Amazon keeps its ASIN behavior; Flipkart uses `pid` when present and otherwise a product-path identity; Myntra uses the numeric product ID in its product path. Tracking query values are excluded from canonical identity. Distinct accounts may save the same retailer item independently.

Flyway migration `V8__add_multi_retailer_saved_products.sql` backfills existing records as Amazon products, adds retailer/product identity and optional description/images/features/categories/specifications/manufacturer/source fields, and adds constraints and indexes. It does not edit earlier migrations or remove existing Amazon uniqueness. JSON values are persisted in PostgreSQL JSONB columns; Hibernate schema mutation remains disabled.

## Enrichment and fallback

Scrape.do API Mode is the primary provider only for Flipkart and Myntra. The adapter calls `GET https://api.scrape.do/` with `token` and the properly encoded target `url`, as described by the [Scrape.do API documentation](https://scrape.do/documentation/). Requests use the configured timeout and response-size bound, disable provider retries and target redirects, and do not enable rendering or proxy/bypass options. The token is backend-only and must be supplied through `SCRAPEDO_API_TOKEN`; never put it in source, logs, frontend configuration, or extension assets.

The extractor checks Product JSON-LD first, then Open Graph/standard metadata, then narrow retailer selectors. Malformed JSON-LD does not fail the whole operation. It maps only recognized fields and validates/sanitizes third-party text, URLs, prices, and identifiers. Currency defaults to INR only for the supported India marketplace when price data exists. Unknown availability remains `UNKNOWN`; missing amounts remain null. CAPTCHA/access-denied/interstitial content is not treated as product data.

Validated page metadata may fill fields missing from provider output. If provider enrichment fails, sufficient extension title metadata can complete the record; otherwise a validated URL-only product remains saved with failure status. No review-personal data, embedded arbitrary HTML, or fabricated metadata is stored. Scrape.do credentials and live quota are not used by automated tests.

Configuration is in `application.yaml` and may be overridden with:

- `SCRAPEDO_API_TOKEN` — required for live Flipkart/Myntra enrichment.
- `SCRAPEDO_ENABLED` — provider switch, default enabled; disable to force fallback behavior.
- `SCRAPEDO_TIMEOUT` — request timeout, default `20s`, bounded to 1–60 seconds.
- `SCRAPEDO_MAX_RESPONSE_BYTES` — maximum response body, default 2,000,000 bytes, bounded to 5 MB.

The development budget is intended to remain ₹0. Verify current Scrape.do account quota, cost, terms, and retailer-permitted use before configuring a live token. The application makes no guarantee that a provider account will prevent overage billing; account-side limits remain authoritative. No live provider smoke test was run.

## Extension and dashboard

The extension recognizes Amazon India, Flipkart, and Myntra URLs with exact hosts and narrow Manifest V3 host permissions. The page metadata extractor reads available title, image, price, currency, and availability only. The service worker submits it only as optional fallback data after the existing user action and existing authenticated API flow. No credentials are exposed to the page/content script. The dashboard now displays a retailer label and Buy link to the original submitted URL, with placeholders for unavailable title, price, image, or availability.

## Tests and verification

Automated coverage added for retailer URL validation, host spoofing, IDs/canonicalization, structured and fallback metadata extraction, malformed JSON-LD, provider error cases, enrichment fallback, user-scoped duplicate behavior, extension detection/page metadata, and dashboard/card rendering.

**Backend:** No backend tests executed. The wrapper's default repository resolved to `C:\.m2\repository` and Maven could not create it (`LocalRepositoryNotAccessibleException`). A temporary repository under `%TEMP%\bringmycart-phase5-maven` avoided touching the existing cache, but Maven Central access failed with `Permission denied: getsockopt` while resolving the Spring Boot 3.5.6 parent POM. Thus compilation, Flyway V8 execution, integration tests, and the full suite remain unverified here. Retry from the normal Windows CMD environment:

```cmd
backend\mvnw.cmd -f backend\pom.xml clean verify
```

**Frontend:** `npm.cmd run test` passed 31/31 tests, then `npm.cmd run build` passed (`tsc -b` and Vite production build).

**Extension:** `npm.cmd run test` passed 35/35 tests; `npm.cmd run typecheck` passed; `npm.cmd run build` passed both the MV3 popup/background build and separate content-script build.

No Chrome manual verification, PostgreSQL migration execution, or live Scrape.do call is claimed in this environment.

**Repository hygiene:** `git diff --check` passed. Git reports only expected source, test, and documentation changes; generated frontend/extension `dist` output is ignored. No credentials were added. Existing line-ending warnings are informational. The repository contains pre-existing uncommitted Phase 4 work; do not discard it.

## Known limitations and risks

- Scrape.do page content can change and may be incomplete; no bypass or browser rendering is attempted.
- Scrape.do account quota/price/terms and retailer permission need operational confirmation before live use. A database save does not guarantee metadata availability.
- Provider response data is not cached/shared between customers; each customer's saved record may trigger a separate provider call. Duplicate requests within one user's save identity are prevented by the database constraint.
- In-memory task execution/recovery retains the existing application lifecycle limitations; this adds no queue or distributed worker.
- Flyway V8 and JPA JSONB mappings must be verified against PostgreSQL through Testcontainers before release.
- Amazon behavior and Axesso tests must remain green; no changes to frontend auth, extension auth, or security policy are intended.

## Manual verification

1. Start the existing development PostgreSQL container and configure existing backend credentials plus a local-only `SCRAPEDO_API_TOKEN` if performing a live provider test.
2. Run the backend and log in using the existing website flow.
3. Save representative Flipkart and Myntra product URLs with the extension; verify the API returns a saved product promptly.
4. Confirm retailer identity, retained original URL, enrichment status, and optional metadata in the dashboard.
5. Confirm a second save of the same retailer identity for the same user is rejected as a duplicate, while a second user can save it independently.
6. Disable Scrape.do or omit its token and verify a valid product URL remains saved and reports the expected fallback/failure state.
7. Confirm Buy opens the original retailer page and Amazon still uses Axesso.
8. Inspect logs to ensure no Scrape.do token or full token-bearing request URL is present.

## Acceptance recommendation

**Conditionally ready for review, not accepted as complete.** The multi-retailer code and tests have been added, but the backend build/Testcontainers suite and Flyway V8 execution need successful verification in a Windows environment with Maven dependency resolution. Confirm provider account terms/quota and permitted data use before enabling live Scrape.do traffic.

## Phase 5B considerations

Phase 5B should be planned separately after Phase 5A verification. Potential concerns include approved provider terms, explicit cache/quota policy, retailer URL edge cases, observability without sensitive request data, and measured extraction quality. It should not introduce recurring refresh or additional retailers without approval.
