# Phase 5B — Retailer Expansion Status

**Status:** Phase 5B.3 extension and dashboard work is implemented. This report does not claim complete Phase 5B acceptance or live extraction success for all retailers.

## Scope

Phase 5B expands supported saved-product retailer coverage from Amazon India, Flipkart, and Myntra to eight active retailers by adding Meesho, AJIO, Nykaa Fashion, Nykaa, and Croma. Tata Neu was subsequently retired by SRS 0.5. This increment extends the Chrome MV3 experience and React dashboard while retaining the Phase 5B.1 backend identity/variant work and Phase 5B.2 Scrape.do extractor.

## Implementation

- The extension URL parser recognizes the currently supported retailer URL examples and applies exact host allowlists. It accepts Meesho `/p/{id}` and retailer product paths ending in `/p/{id}` for AJIO, Nykaa Fashion, Nykaa, and Croma. Existing Amazon ASIN, Flipkart `pid`/path, and Myntra numeric ID handling remains in place. Tata Neu URLs are rejected.
- Canonicalization removes irrelevant query tracking. Nykaa verifies an optional `productId` agrees with the path ID; AJIO keeps its `_suffix` in canonical URL while separating the numeric product ID and exposing the suffix as a variant label.
- Manifest V3 content-script matches and host permissions are restricted to HTTPS root and `www` hosts for the eight active retailers plus the existing local BMC web/API development origins. The existing prompt, login/authorization, and Save Item flow remain. Save submits the original URL through the existing API; there are no provider calls from the extension.
- Extension page fallback data now prefers Product JSON-LD only when `sku`, `productID`, or `productId` matches the parsed page product ID, then falls back to the existing metadata tags. Malformed JSON-LD is ignored, images must resolve to HTTPS, and missing values remain absent.
- The dashboard maps all eight active retailer values to readable names, retains original retailer Buy links and existing refresh/delete controls, and displays `variantLabel` only when the backend persisted one. Historical retired-retailer rows use a generic Unsupported retailer label and keep the original link.
- No size/color is inferred from arbitrary page controls. The only variant label exposed by this extension increment is the URL-identifiable AJIO suffix. The other four retained Phase 5B examples do not encode a selected variant.

## Retired Tata Neu and database compatibility

Flyway V9 may already be applied and is not edited. Its CHECK constraint continues to allow the historical database literal `TATA_NEU`, preserving existing rows. The persistence converter maps that value to the generic `Retailer.UNSUPPORTED` sentinel on reads and writes the legacy literal if an existing row is updated. New Tata Neu URLs are rejected by backend validation; the extension no longer has Tata Neu URL support, host permissions, or page detection; and the provider pipeline does not route unsupported legacy records to Scrape.do. Existing records are not deleted automatically. A preview-first procedure for future, explicitly approved cleanup is in `docs/operations/retired-tata-neu-cleanup.md`.

## Database and backend compatibility

The retirement changes backend URL validation and persistence compatibility but does not alter the database schema or saved-product API shape. It continues to use the retailer and variant response fields introduced in Phase 5B.1/V9 and the existing saved-product request contract. Backend URL validation rejects new Tata Neu saves, authenticated ownership and duplicate prevention remain authoritative, and URL-only fallback remains available for supported retailers. Integration coverage verifies rejected new Tata Neu saves and retrieval of a legacy Tata Neu row as `UNSUPPORTED` while retaining its original URL.

## Verification performed

Retirement verification is recorded separately below after the commands for this change are executed; the older Phase 5B.3 counts below describe the earlier increment and are not claims about this retirement change.

- Extension: `npm.cmd run test` — **51 tests passed** (4 test files).
- Extension: `npm.cmd run typecheck` — passed.
- Extension: `npm.cmd run build` — passed; Vite produced popup/background output and a separately built content script.
- Frontend: `npm.cmd run test` — **37 tests passed** (7 test files).
- Frontend: `npm.cmd run build` — passed (`tsc -b` and Vite production build).
- The extension tests cover the six representative URL examples, canonical identity, host/path rejection, AJIO variant suffix handling, matched structured data, malformed JSON-LD, and safe image handling. Dashboard tests cover labels and variant display.
- No real retailer pages, Scrape.do requests, Chrome installation, or end-to-end save flow were exercised in this environment. Passing parser and sanitized DOM tests do not establish live retailer metadata availability.

## Retailer limitations and manual verification

Retailer page structures and URL formats may change. The supplied URLs prove only that the extension parser handles those examples; live product metadata extraction per retailer remains unverified. Nykaa rejects a `productId` query value that disagrees with the path. AJIO suffix labels are mechanically title-cased from the URL suffix; they are not independently verified against retailer variant controls. Other product variants remain unspecified unless a reliable identity is supplied by backend URL validation. Tata Neu is no longer in active support; the database literal is retained only for backward compatibility with rows created before retirement.

Manual Chrome check:

1. From `extension/`, run `npm.cmd run build`, then open `chrome://extensions` and reload the unpacked extension from `extension/dist` (or Load unpacked from that folder if not installed).
2. Open representative URLs for the five retained Phase 5B retailers (Meesho, AJIO, Nykaa Fashion, Nykaa, and Croma) and the existing Amazon, Flipkart, and Myntra retailers. Confirm the prompt and popup identify supported product pages; verify the retired Tata Neu URL is rejected.
3. Connect using the existing extension authentication flow, save one product, and confirm the backend response drives success/duplicate feedback. Verify the original URL is retained in the dashboard Buy link.
4. Confirm the AJIO sample shows the URL-derived `Blue` label. Confirm no size/color appears for samples without a selected variant identifier.
5. Check title/image/price/availability for live retailer pages only if the backend Scrape.do configuration and account terms/quota permit; no live request is necessary for URL recognition testing.

## Recommendation

Phase 5B retailer retirement is **conditionally ready for review**: active Tata Neu paths are removed and frontend/extension checks passed. Backend runtime compatibility still requires verification in the normal Windows developer environment because this sandbox cannot compile against the cached Log4j artifact.

## Tata Neu retirement verification

- Backend `mvnw.cmd clean verify` could not start: the sandbox JVM resolves `user.home` to `C:\`, and Maven cannot create `C:\.m2\repository`.
- A temporary Maven repository under `%TEMP%` was tried without modifying the existing cache; dependency resolution was blocked by network `Permission denied: getsockopt` from Maven Central.
- Reusing `%USERPROFILE%\.m2\repository` allowed Maven to begin compilation, but `clean` was denied while deleting `backend/target`, and `verify` stopped during production compilation with an access error on `log4j-api-2.24.3.jar`. No backend tests completed; run `mvnw.cmd clean verify` from the normal Windows development CMD to validate the backend changes.
- Frontend: `npm.cmd run test` — **41/41 passed**; `npm.cmd run build` — passed. The first run exposed only a too-strict assertion matcher in the new generic legacy label test; the matcher was corrected and the suite rerun successfully.
- Extension: `npm.cmd run test` — **50/50 passed**; `npm.cmd run typecheck` — passed; `npm.cmd run build` — passed. Build output generated after removing Tata Neu host patterns.
- No live retailer requests or browser installation tests were run. No commit or push was made.
