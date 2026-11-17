# Phase 3 Report — Saved Products and Customer Dashboard

## 1. Objective and scope

Phase 3 delivers authenticated Amazon India saved-product management, backend-only product metadata enrichment (now provided by Axesso RapidAPI), manual metadata refresh, and the React customer dashboard. It does not implement the Chrome extension, affiliate redirects, rewards, gift cards, scheduled price monitoring, or Amazon review-summary display.

## 2. Implemented functionality

- Strict HTTPS Amazon India URL validation for supported `/dp/{asin}` and `/gp/product/{asin}` paths; the exact normalized ten-character child ASIN is retained.
- Owner-scoped save, list (stable paginated response), delete, and manual-refresh APIs. PostgreSQL prevents a duplicate `(user_id, asin)` save while allowing another customer to save the same ASIN.
- Transaction-after-commit, bounded asynchronous enrichment with `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED` states. Provider failures retain the saved product and expose only sanitized categories.
- Backend-only Axesso RapidAPI adapter using the verified Amazon Product Details GET endpoint, `url` query parameter, RapidAPI key/host headers, configurable timeouts, response validation, ASIN-match validation, and metadata supported by the existing API contract.
- Explicit customer refresh/retry with a five-minute default cooldown; no scheduled price refresh. Existing successful metadata is retained if a replacement refresh fails.
- React + TypeScript + Vite dashboard with registration/login, memory-only access token, protected routes, pagination, save/delete/refresh actions, metadata states, and responsive product cards.

## 3. Architecture and database decisions

The approved MVP uses one customer-owned `saved_products` record, not the future shared retailer-reference model. This makes ownership isolation and exact-variant uniqueness straightforward. A trade-off is that different customers saving the same ASIN can make separate provider requests.

Flyway migrations are:

- `V2__create_saved_products.sql` — saved-product data, metadata snapshot fields, JSONB fields reserved for future review data, ownership FK, constraints, and indexes.
- `V3__add_saved_product_enrichment_tracking.sql` — sanitized enrichment failure/recovery tracking.
- `V4__add_saved_product_manual_refresh_tracking.sql` — manual-refresh cooldown tracking.

The worker never holds a database transaction open during the provider HTTP call. Recovery is conservative and disabled by default; it can only recover stale/transient work within configured bounds. It never automatically refreshes completed products.

## 4. Backend API surface

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/api/v1/saved-products` | Authenticated save; returns `201` with `PENDING` status without waiting for metadata. |
| `GET` | `/api/v1/saved-products` | Authenticated, owner-scoped stable pagination response. |
| `DELETE` | `/api/v1/saved-products/{id}` | Owner-only deletion; returns `204`. |
| `POST` | `/api/v1/saved-products/{id}/refresh` | Owner-only asynchronous refresh/retry; returns `202`, with controlled conflicts/cooldown. |
| `GET` | `/api/v1/auth/csrf` | Initializes the readable CSRF cookie before cookie-authenticated browser POST requests. |

## 5. Authentication and CSRF correction

Access tokens remain in browser memory only. The rotating refresh token remains an HttpOnly, SameSite refresh-path cookie. Refresh and logout remain CSRF-protected. The browser bootstrap now requests `/api/v1/auth/csrf` before `/api/v1/auth/refresh`, then sends the readable CSRF cookie value in the `X-XSRF-TOKEN` header. This fixes the observed first-refresh `403` caused by a missing cookie/header pair, without exempting refresh from CSRF. Refresh-token rotation, replay detection, family revocation, and single-flight refresh behavior are retained.

The developer manually verified that repeated browser reloads retain the authenticated dashboard session. This is manual browser verification; no automated browser run is claimed.

## 6. Axesso RapidAPI and Amazon Reviews status

The adapter maps Axesso fields supported by the existing model: exact ASIN, optional parent ASIN, title, manufacturer as brand, primary/fallback image, retail and selling prices, INR for Amazon.in when currency is absent, derived discount, rating, review count, availability, and provider timestamp when present. The application applies a process-local configurable monthly request cap (default 100); RapidAPI account usage remains authoritative. The API key stays in backend configuration only and is not returned or logged. Description, gallery images, features, categories, and specifications are not persisted or added to the REST contract because the current model/API does not support them.

Aggregate rating and review count are implemented when supplied. Although `review_summary` and `review_topics` columns exist, the enrichment pipeline does not persist or expose provider customer summaries or topics. The Amazon Reviews popup/modal is **not complete**. Storage/display remains blocked until account-specific Axesso metadata usage and retention permissions are verified; no review content is fabricated.

## 7. Verification results

- Axesso provider replacement: implementation and mocked tests are present, but this environment could not compile or execute them. The normal Maven repository resolved to the inaccessible `C:\.m2\repository`; an explicit user-level repository reached compilation but the compiler was denied access to `log4j-api-2.24.3.jar`. No live RapidAPI request was made.
- Final developer-local backend verification: `mvnw.cmd clean verify` passed **48/48 tests**, with 0 failures, 0 errors, and 0 skipped.
- Frontend verification: `npm.cmd run test` passed **21/21 tests across 6 files**; `npm.cmd run build` completed successfully.
- Current review `git diff --check` completed with no whitespace errors.
- This execution identity could not independently rerun backend tests: Maven could not create its configured local repository at `C:\.m2\repository`, and Docker access was denied. The 48/48 backend result above was confirmed by the developer in the normal Windows development environment.

## 8. Security review

- Saved-product identity derives from the authenticated JWT; no client-provided user ID is trusted.
- Saved-product repository operations are owner-scoped; other users receive non-disclosing not-found behavior.
- Axesso RapidAPI keys, JWT key files, environment files, build outputs, `node_modules`, and local key material are ignored by Git configuration.
- No raw provider error, bearer token, refresh token, or key material is included in API responses or intended logs.
- CORS uses configured origins and credentials rather than a wildcard with credentials.

## 9. Known limitations and next-phase prerequisites

- Node `24.15.0` or newer is recommended. Node `24.3.0` emits dependency-engine warnings for the current jsdom dependency set.
- Verify Axesso account-specific terms before enabling review-summary/topic persistence or an Amazon Reviews modal.
- Confirm production secret management, HTTPS cookie settings, CORS origins, and provider retention obligations before deployment.
- Chrome MV3 save flow and secure authorization-code/PKCE handoff remain a later approved phase.

## 10. Completion recommendation

Phase 3 is **ready for approval** for its implemented scope: the developer confirmed all 48 backend tests pass; frontend tests and production build pass; and repeated dashboard refreshes were manually verified. Amazon Reviews summary/topic persistence and popup remain blocked until account-specific Axesso usage permissions are confirmed. This limitation does not block approval of the delivered saved-products scope.
