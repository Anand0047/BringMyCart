# Phase 4 Plan — Chrome Manifest V3 Extension

## Status and scope

This document plans Phase 4. Step 2 has established the MV3 TypeScript extension shell, popup, service worker, and Amazon India URL parser. Authentication, backend API integration, and saving remain unimplemented. The MVP targets Amazon India product pages on `amazon.in` and `www.amazon.in`. It does not interact with Amazon accounts, carts, or checkout, and does not activate affiliate or rewards features. Axesso RapidAPI remains backend-only.

## Existing contracts inspected

The existing API contract is:

| Operation | Contract | Authentication / behavior |
| --- | --- | --- |
| Save | `POST /api/v1/saved-products` with `{"url":"https://www.amazon.in/dp/ASIN"}` | Bearer JWT required. User ID is derived by the backend from JWT subject. Returns `201` with saved product ID, ASIN, original/canonical URL, available metadata, and enrichment status. |
| List | `GET /api/v1/saved-products` | Bearer JWT; user-scoped stable `PageResponse`. Reusable for a later extension collection view, not needed for Save Item itself. |
| Delete | `DELETE /api/v1/saved-products/{id}` | Bearer JWT; owner-only. Not needed for the first extension MVP. |
| Refresh | `POST /api/v1/saved-products/{id}/refresh` | Bearer JWT; owner-only; CSRF is applicable only to browser cookie-auth flows; extension should authenticate with bearer header and not send refresh cookies. |

The current authentication API has registration/login, `GET /api/v1/auth/csrf`, cookie-backed `POST /api/v1/auth/refresh`, logout, and `/me`. The React app keeps access JWT only in module memory, uses an HttpOnly `bmc_refresh` cookie, initializes the readable CSRF cookie before refresh, and sends `X-XSRF-TOKEN` for cookie-backed POSTs. This browser session is not an extension authentication contract. Current backend CORS allowlist is configured for the React web origin; extension origins are not established. The extension foundation now exists; see Step 2 status below.

## Proposed extension architecture

Use a small TypeScript MV3 extension, built with Vite only if the build setup remains simple. Keep shared logic framework-free; the popup may use React only if reuse/maintenance warrants it. Proposed layout:

```text
extension/
  manifest.json
  src/
    popup/                 # status, detected product, Save Item, sign-in, dashboard link
    background/            # MV3 service worker: auth handoff, API calls, messages
    content/               # optional minimal product-page metadata reader
    auth/                  # PKCE, state, code exchange, session lifecycle
    amazon/                # URL parser, supported-host check, ASIN validation
    api/                   # typed BMC endpoint client and safe error mapping
    shared/                 # message types and small common utilities
  test/                     # parser, state machine, API/auth-flow tests
```

Responsibilities:

- `manifest.json`: MV3 declaration, popup, service worker, narrowly scoped Amazon/BMC hosts, and only the `identity` and `storage` permissions if the selected flow and persistence model require them.
- Popup: query the active tab, show supported/unsupported status and permitted available fields, initiate explicit sign-in from a user gesture, offer Save Item, and link to the dashboard. It must not treat page metadata as trusted identity or authoritative product data.
- Service worker: own the authenticated API calls, token lifecycle, auth-flow correlation, and validated popup/content messages. Persist only the minimum session material selected after security review; reconstruct work after worker suspension from durable session storage, not globals alone.
- Content script: preferably omit initially and parse the active tab URL in the popup/service worker. Add a narrowly matched content script only if product metadata unavailable from the URL is needed, and read only explicitly permitted display fields.
- Amazon parser: accept only HTTPS, exact supported hostnames and supported product URL patterns; reject lookalike hosts; preserve the child ASIN; never fetch arbitrary submitted URLs.
- API client: call only configured BMC API origins, attach the extension access token as a Bearer header, never send the web refresh cookie, and normalize API errors without logging secrets.
- Tests: parser and host allowlist unit tests, message validation tests, API contract tests, and backend integration tests for authorization-code redemption and account isolation.

### Least-privilege manifest proposal

- `permissions`: `identity` for `launchWebAuthFlow` and `storage` only if session data must survive service-worker suspension.
- `host_permissions`: exact Amazon India origins `https://www.amazon.in/*` and `https://amazon.in/*`; exact production BMC API origin and development localhost origin should be separated by build configuration. Do not grant `<all_urls>`.
- No `activeTab` unless required by the chosen popup tab-query design; avoid `tabs`, `cookies`, scripting, and broad content-script access unless a concrete requirement demonstrates need.
- Do not permit incognito initially (`incognito: "not_allowed"`) to avoid accidental cross-context account/session sharing. Revisit only with an explicit isolation and UX decision.

Chrome’s documented `identity.getRedirectURL()` creates an `https://<extension-id>.chromiumapp.org/*` callback for `identity.launchWebAuthFlow()`. Chrome documents the callback as the URL at which it closes the auth window and returns the redirect URL to the extension. This makes the production extension ID and callback an allowlisted deployment input. Development and production IDs may differ; callback registration and packaged-ID stability must be tested before release. See [Chrome Identity API](https://developer.chrome.com/docs/extensions/reference/api/identity) and [Chrome incognito modes](https://developer.chrome.com/docs/extensions/reference/manifest/incognito).

## Proposed secure authentication sequence

The extension must use a distinct extension session; it must not attempt to reuse the React app’s refresh cookie. Proposed authorization-code + PKCE S256 flow:

```mermaid
sequenceDiagram
  participant P as Extension popup
  participant W as MV3 service worker
  participant C as Chrome identity flow
  participant R as BringMyCart React login
  participant B as Spring Boot backend
  P->>W: Sign in (user gesture) + pending save intent
  W->>W: Generate state, nonce, PKCE verifier/challenge
  W->>C: launchWebAuthFlow(registered BMC authorize URL)
  C->>R: Open login/consent flow
  R->>B: Authenticate existing BMC account
  B->>B: Validate client, redirect, state binding, PKCE challenge
  B-->>C: Redirect to exact registered chromiumapp.org callback with one-time code + state
  C-->>W: Return callback URL
  W->>W: Validate exact callback and state; reject replay/expiry
  W->>B: Exchange code + verifier via HTTPS POST
  B->>B: Atomically consume code; validate PKCE/client/redirect/user
  B-->>W: Short-lived extension access token (and approved session renewal mechanism)
  W->>B: POST /api/v1/saved-products with Bearer JWT and original URL
  B->>B: Validate JWT and derive owner; validate URL and save
  B-->>W: 201 saved product
  W-->>P: Success/duplicate/error feedback
```

Never place access/refresh JWTs in a URL. The callback may carry only the short-lived one-time authorization code and opaque state; redeem the code immediately over HTTPS and remove it from transient flow state. Bind code to the authenticated user, extension client ID, exact registered callback, PKCE S256 challenge, state, short expiry, and a single-use database state. Use unpredictable state/nonce, constant-time comparison where applicable, expiry, atomic consumption, replay tests, and generic error responses. Retain the original tab ID and save intent only for that flow, and resume only after successful code redemption and validation of the same supported Amazon URL.

### Token/session storage and lifecycle recommendation

The extension needs an access token to call the existing API, while the current rotating refresh token is cookie-only and scoped to the web auth path. Options are:

1. **Short-lived access token in `chrome.storage.session`; no extension refresh token initially.** This is the simplest secure baseline and survives service-worker suspension for the browser session, but after expiry the user signs in again. Chrome documents session storage as in-memory storage for the extension lifetime and exposes it to extension contexts subject to access-level settings. Restrict access to trusted extension contexts and never expose it to content scripts. This is the recommended first delivery unless usability demands refresh.
2. **Extension-specific rotating opaque refresh credential in `chrome.storage.session`.** Better continuity, but adds server-side session family, rotation/replay/revocation, logout, and secure storage requirements. Do not reuse browser cookie refresh tokens or use `chrome.storage.local` by default.

The service worker can be suspended/restarted, so module globals are not durable. A pending interactive callback should be short-lived and safely fail closed after restart; never persist a PKCE verifier longer than needed. Extension logout must revoke its extension session/code family server-side and clear session storage. Access-token expiry requires reauthentication or a separately approved extension refresh design. Browser profiles naturally isolate extension storage; incognito is disabled for MVP pending confirmation of desired behavior. Chrome notes `storage.local`/`storage.sync` are shared between regular and incognito extension processes, which is another reason not to use them for auth material.

## Backend changes required for extension auth

Existing registration/login and saved-product APIs remain reusable. New dedicated extension authorization is required; do not pass extension auth through the React website’s cookie refresh mechanism.

Proposed backend work (API paths intentionally not finalized):

1. Authorization initiation endpoint accepting a registered extension client, exact redirect URI, state, nonce, and PKCE S256 challenge; validates request and creates short-lived server-side authorization transaction.
2. Login/consent integration that uses the existing BMC identity/account and binds successful customer authentication to that transaction. The existing web login UI can be extended only after UX and CSRF details are approved.
3. Callback redirect that issues a one-time authorization code and returns only code + state to the registered Chrome callback.
4. Token exchange endpoint that atomically consumes the code and verifies client, redirect, PKCE verifier, expiry, user binding, and replay status; returns a short-lived extension access token.
5. Explicit extension logout/revocation endpoint if an extension refresh/session credential is approved. If access-only v1 is selected, logout clears local token and acknowledges access remains valid until its short expiry; revoking access immediately would require server-side token/session checks.
6. CORS review for extension origin only if browser extension fetch requires it; add exact production extension origin and an explicitly development-only origin. Never wildcard credentialed CORS. Determine whether Chrome extension cross-origin requests require host permissions and how backend origin checks interact with callback navigation.

These additions require identity-owned authorization-code/session entities, Flyway migration(s), rate limits, audit events without token/code values, API error contracts, and integration tests for wrong redirect/client, bad verifier/state, expiry, concurrent redemption, replay, disabled user, logout, and ownership. The existing saved-product service must continue deriving `user_id` only from a validated JWT.

## Eight-step implementation plan

| Step | Objective | Likely files | Dependencies | Acceptance criteria | Tests and security checks |
| --- | --- | --- | --- | --- | --- |
| 1. Extension contract and authorization decisions | Approve callback, stable extension ID, session revocation, consent UX, storage, origins, and incognito policy. | `docs/PHASE_4_PLAN.md`, `docs/ARCHITECTURE.md`, proposed ADR only if approved | Stable local extension ID and exact registered dev callback | Exact registered production/dev origins; no code | Review URL/API constraints; ensure no token URLs or wildcard origins |
| 2. Backend extension authorization | Add authorization transaction/code exchange, PKCE, one-time expiry, user binding and optional revocation. | identity API/application/domain/infrastructure, Flyway, SecurityConfiguration, tests, README/report | Step 1; stable production extension ID/callback | Code exchange succeeds once only; existing web auth unaffected | PostgreSQL integration tests for replay, invalid state/verifier/redirect, expiry, races, disabled accounts; CSRF/CORS/auth regression |
| 3. Extension build and manifest shell | Create MV3 TypeScript build, popup shell, service worker registration, minimal permissions/origins. | `extension/manifest.json`, Vite/TS config, `src/background`, `src/popup`, `package.json`, tests | Step 1 and API origin/callback decisions | Loads unpacked in Chrome with only required origins/permissions | Manifest validation/build; permission audit; no all-sites access |
| 4. Amazon URL detection and popup | Validate active tab URL, exact hosts/routes/ASIN; show available title/image only if permitted; unsupported feedback. | `extension/src/amazon`, popup, optional content reader, tests | Step 3; permitted page metadata decision | Supported `/dp` and `/gp/product` child ASIN recognized; deceptive URLs rejected | Parser tests, malformed URL/host/variant tests; no arbitrary fetch or Amazon credentials |
| 5. Secure sign-in handoff | Implement interactive auth-code/PKCE state, callback validation, code exchange, short-lived session storage, recovery after worker restart. | `extension/src/auth`, background messaging, backend client, identity tests | Step 2–3; approved token storage/expiry | Only registered callback + fresh state + code + verifier establishes session | State/replay/expiry/callback mismatch tests; inspect logs/storage/URL for token leakage |
| 6. Save Item API integration | Call existing `POST /api/v1/saved-products` with tab URL and extension bearer token; map duplicate/errors. | `extension/src/api`, popup, background, tests | Steps 4–5 | User-owned save; immediate confirmation; correct duplicate/401/validation/provider-pending messages | Mock API tests and backend authorization/ownership test; never send client user ID or refresh cookie |
| 7. Session expiry, logout, hardening and browser QA | Add explicit logout/reauth behavior, suspension-safe flow, incognito policy, origin/CSP/message hardening. | extension auth/background/manifest, backend revocation if selected, docs | Steps 2–6 | Expired session fails closed; logout clears/revokes selected session; no cross-tab/profile confusion | Chrome manual tests, restart/service worker suspension, incognito disabled, XSS/message-origin, CSP and permission review |
| 8. Release readiness and documentation | Package review, Chrome Web Store preparation, final traceability and test report. | README, architecture, SRS clarification only if approved, `docs/PHASE_4_REPORT.md`, extension release assets | Steps 1–7; listing and privacy policy decisions | Reproducible build, approved privacy disclosures, store listing URL tracked as TBD until published | Full backend + extension tests; packaged permission audit; no secrets/artifacts staged |

Each step is independently reviewable. Step 2 implementation must wait for Step 1 approval of the exact callback and session decisions. This plan does not authorize feature implementation.

## Confirmed versus proposed decisions

**Confirmed:** MV3; Amazon India only; shared BMC customer account; existing backend save contract; backend owns identity/authorization and Axesso RapidAPI metadata; extension must not access Amazon credentials/cart; current BMC web access tokens are memory-only and refresh uses HttpOnly cookie.

**Proposed:** TypeScript; least-privilege popup + service worker; omit content script until needed; use Chrome Identity launch flow with one-time code + PKCE S256; use session-only extension access token in `chrome.storage.session` initially; disallow incognito in MVP; exact-host permissions and separate development/production origins.

## Follow-up implementation status — Authentication and save request

The developer has approved server-tracked revocable extension sessions, short-lived session-bound JWTs, PKCE S256 authorization codes, website-session authorization, and authenticated save calls. This work is not yet implemented. Inspection found no `key` field or signing asset in `extension/public/manifest.json`, no recorded extension ID in repository configuration, and no existing callback registration. Chrome profile data was inaccessible in this environment, so no actual installed ID or `chrome.identity.getRedirectURL()` result could be verified. No callback URI or extension ID was invented or hardcoded.

To establish the local callback, load `extension/dist` via `chrome://extensions` → Developer mode → Load unpacked, copy the displayed extension ID, and derive the callback with `chrome.identity.getRedirectURL()` in the unpacked extension. If that ID must remain stable across removal/reload, configure a development public manifest key derived from a private signing key kept outside Git; do not commit the private key. Record the resulting exact callback URI in local backend configuration and register the matching exact URI server-side. Production callback registration can then be configured separately for the stable store extension ID.

Independent frontend auth error handling was updated to provide generic invalid-credential messages and actionable registration/network errors without revealing account existence during login. Verification: 23 frontend tests passed and the frontend production build passed. Backend authorization-code/session endpoints, migrations, service-worker auth/save integration, in-page auth synchronization, backend/extension tests, and Chrome verification remain outstanding pending the local callback setup.

**Unresolved, approval required:** Chrome Web Store extension ID and exact callback URI; backend authorization endpoint shapes and consent UX; whether access-only reauthentication is acceptable or an extension-specific rotating refresh credential is needed; access-token audience/client claim and lifetime; token revocation semantics; CORS treatment for `chrome-extension://<id>`; development extension ID strategy; whether product title/image may be read from Amazon DOM and permitted metadata policy; incognito behavior; dashboard install URL and privacy disclosures.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Extension ID/callback changes break registered redirects | Stabilize signing/package identity before registration; test exact callback for dev and store builds. |
| Auth code interception/replay | PKCE S256, exact redirect matching, one-time code, short expiry, state binding, atomic consumption. |
| MV3 worker suspension loses in-memory operation | Persist minimal expiring flow metadata in session storage; make code exchange single-use and recover by restarting sign-in safely. |
| Token theft through content scripts or page messages | Keep tokens only in trusted extension service-worker/popup context; content scripts never access auth storage or tokens. |
| Browser profile/incognito session confusion | Session storage per normal profile; initially disallow incognito; never use sync storage for tokens. |
| Host/CORS overbroad access | Exact Amazon and BMC hosts; separate dev origin; no wildcard CORS/host permissions. |
| Page metadata is stale or manipulated | Treat title/image as optional display hints; backend validates URL/ASIN and provider enriches server-side. |
| Duplicate button presses or lost response | Backend unique `(user_id, asin)` remains final authority; report duplicate safely and query/list only if later required. |
| Provider terms/policy not verified | Keep all Axesso RapidAPI credentials and calls backend-side; no affiliate activation; verify metadata terms before storing additional provider fields. |

## Step 2 implementation status — Extension foundation

**Implemented:** the `extension/` directory now contains a Vite/TypeScript MV3 foundation without React, a popup, a module service worker, a typed `GET_ACTIVE_PRODUCT` message, and the URL parser and unit tests. The parser accepts exactly the approved `/dp/{ASIN}`, `/gp/product/{ASIN}`, and `/gp/aw/d/{ASIN}` path patterns on HTTPS `amazon.in` or `www.amazon.in`, and canonicalizes to `https://www.amazon.in/dp/{ASIN}`. The popup shows page support, ASIN and canonical URL, keeps Save Item disabled, and opens the dashboard URL from `VITE_BMC_DASHBOARD_URL` (default `http://localhost:5173/dashboard`).

The manifest grants only `activeTab`; it does not request host permissions, identity, storage, tabs, scripting, or broad site access. The popup-triggered active-tab query is used for detection. No content script, authentication, network API integration, saving behavior, icons, or product metadata extraction was added. No icon is required by the current manifest; Chrome may show its default extension icon until a branded asset is approved.

**Verification in this environment:** `npm.cmd install` completed and generated `package-lock.json`. `npm.cmd run typecheck` passed. `npm.cmd run test` and `npm.cmd run build` could not load `vite.config.ts`: esbuild reported access denied while traversing `../../..` from this workspace path containing a space. `npm.cmd audit` also could not reach the npm audit endpoint and could not write its cache log due to access denied. Therefore the bundled output and manifest-to-output paths have not yet been verified, and no Chrome manual load has been performed.

**Load unpacked after a successful build:** run `npm.cmd run build` from `extension/`. In Chrome, open `chrome://extensions`, enable Developer mode, select **Load unpacked**, and choose `extension/dist`. The manifest declares `popup.html` and `background.js`; verify both are present in `dist` before loading. This instruction describes the intended workflow; loading has not been tested in this environment.

### Step 2 verification still required

Re-run from a Windows shell with filesystem access for the Vite/esbuild process:

```cmd
cd extension
npm.cmd run test
npm.cmd run typecheck
npm.cmd run build
npm.cmd audit
```

If audit returns findings, inspect dependency chains and distinguish development tooling from runtime extension code; prefer compatible patch/minor updates and do not use `npm audit fix --force` without approval. Then inspect `dist/manifest.json`, `dist/popup.html`, and `dist/background.js`, and manually load `dist` in Chrome.

## Current implementation update — Secure authentication and saving

This section supersedes the earlier follow-up status above. Phase 4 authentication and save integration are now implemented in the working tree; the extension is **not yet release-ready** because backend verification and live Chrome callback verification remain.

### Development callback registration and stability

The supplied development ID is `mlogpmpajiokfmjnlmehmpgphbbflpem`; the registered development callback is exactly `https://mlogpmpajiokfmjnlmehmpgphbbflpem.chromiumapp.org/bmc-auth`. Backend allowlisting is configurable through `BMC_EXTENSION_DEV_CALLBACK_URI` and the extension's `VITE_EXTENSION_CALLBACK_URI`. Before opening the authorization flow, the service worker calls `chrome.identity.getRedirectURL("bmc-auth")` and compares its exact HTTPS origin and path with the configured callback. A mismatch fails closed. The actual Chrome runtime value could not be observed in this execution environment because the Windows Computer Use bridge was unavailable; verify this comparison after reloading the built extension.

For local builds, copy `extension/extension.env.example` to `extension/.env.local` and adjust the callback only after comparing it with Chrome's runtime value. `.env.local` is ignored by Git.

The unpacked ID is not promised to survive moving or reinstalling the extension. To stabilize it, package the development extension using Chrome's **Pack extension** flow and keep its generated private `.pem` signing key outside the repository in a protected local location. Derive the matching public key and configure that public key in the development manifest/build input (`key`); the public key may be committed, but never the private `.pem`. Reload the unpacked build and confirm its displayed ID and `getRedirectURL("bmc-auth")` before changing the exact backend callback allowlist. Production must register a separate callback for its separately stable extension ID.

### Implemented handoff and session lifecycle

The React `/extension/authorize` page restores the ordinary website session through the existing CSRF initialization and HttpOnly refresh-cookie flow. It presents an explicit authorization action; it does not read cookies or expose website tokens to the extension. The backend accepts that request only with a website JWT, checks the configured client ID and exact redirect URI, then creates a two-minute authorization code. Codes and state are stored as SHA-256 hashes; exchange requires the matching client, exact redirect, state and PKCE S256 verifier, and atomically consumes a code once.

Flyway V5 adds `extension_authorization_codes`, `extension_sessions`, and `extension_refresh_tokens`. Exchange creates a separate seven-day server-tracked extension session, an opaque rotating refresh token (only its hash is stored), and an eight-minute RS256 access JWT containing the session ID. A security filter checks the session's active and unexpired state on every extension-JWT request. Refresh uses a row lock; reusing a rotated token revokes that extension session. `/api/v1/extension-auth/logout` revokes it and is reported as successful only after the backend responds. V7 additionally binds the separate extension session to the website refresh-token family that authorized it; website logout or a subsequent successful login using that browser's existing refresh cookie invalidates the bound extension session. Extension credentials remain independently revocable and separate.

| Endpoint | Behavior |
| --- | --- |
| `POST /api/v1/extension-auth/authorize` | Website Bearer JWT required; creates code and returns exact registered callback URL. Website API CSRF header remains in use. |
| `POST /api/v1/extension-auth/token` | Public-client code exchange with `code`, `codeVerifier`, `state`, `clientId`, and `redirectUri`; no cookies. |
| `POST /api/v1/extension-auth/refresh` | Rotates extension refresh token from JSON body; no cookies. |
| `POST /api/v1/extension-auth/logout` | Revokes the matching server session from its refresh token. |

The existing `POST /api/v1/saved-products` contract is reused with the current Amazon URL. The backend remains the authority for URL validation, ASIN normalization, ownership, duplicate handling, and metadata enrichment. Save API calls use an extension Bearer token with `credentials: omit`; Axesso RapidAPI credentials remain backend-only. The content script receives only authentication status and safe save-result messages. Credentials live in `chrome.storage.session`, restricted from content scripts with `TRUSTED_CONTEXTS`; they are never sent to popup/content/page code. Extension session state is lost when the browser session ends and users must authorize again. The access/refresh token values are not persisted to local or sync storage.

The service worker synchronizes auth-state changes to open Amazon tabs, resumes prompt state without page reload, guards concurrent authorization/refresh/save requests, and handles duplicate, expired-session, server and network outcomes. The in-page actions are **Save Item** and **Not Now** for authenticated users; the unauthenticated action starts the Chrome identity flow. The popup also provides extension sign-in and server-backed logout.

The React login and registration pages now map API validation, duplicate-account, invalid-credential, network and server errors to user-safe messages; login remains generic to avoid account enumeration.

Spring Security CSRF remains active for cookie-authenticated website POSTs. It exempts only extension credential endpoints that do not use cookies, and only treats explicit Bearer-authenticated requests as non-CSRF ambient-auth requests. CORS remains an exact-origin allowlist; development includes `http://localhost:5173` and `chrome-extension://mlogpmpajiokfmjnlmehmpgphbbflpem`. Do not replace these with a wildcard. Update backend allowlists and manifest host permissions for the separate production extension/API origin.

### Verification and manual setup status

- Extension unit tests: 17 passed; TypeScript typecheck passed; production build passed (using Vite's `runner` config loader to avoid this environment's esbuild parent-directory access restriction).
- Frontend tests: 26 passed across 7 files; TypeScript production build passed.
- Backend: `mvnw.cmd clean verify` could not complete here. Maven `clean` was denied while removing `backend/target`; a subsequent verification reached test compilation but Java 21 failed while closing a dependency JAR (`java.nio.file.AccessDeniedException` from `WindowsLinkSupport.getRealPath`). Main sources emitted no Java diagnostics in the forked compile attempt, but the Maven goal still exited with the JDK filesystem error. Backend tests did not execute, and no backend test result is claimed. Integration coverage now includes exchange/replay, verifier/state/redirect/expiry validation, refresh rotation replay revocation, logout, CSRF enforcement, and extension-origin CORS; rerun `mvnw.cmd clean verify` from the normal Windows CMD environment.
- Chrome was not manually exercised. After `npm.cmd run build`, load `extension/dist` at `chrome://extensions`, inspect the service worker console, and verify `chrome.identity.getRedirectURL("bmc-auth")` equals the registered callback. Test signed-in consent, signed-out login return, save success/duplicate, logout, refresh, callback mismatch, and prompt synchronization.

### Remaining security and release decisions

- Stabilize the local unpacked extension ID using a protected signing key and verify the live callback URI; do not check the private key into Git.
- Register a separate production extension client ID and callback URI before release.
- Choose the production API origin and add only that origin to manifest host permissions, backend CORS, and callback/client configuration.
- Review Chrome Web Store privacy disclosures for extension session tokens and Amazon page URL access. Incognito remains disabled.
- Run backend migration, authorization-code replay, token rotation, session revocation, CORS, and saved-product ownership tests successfully in the normal Windows developer environment.

## Account synchronization, profile names and popup update

The website now sends the extension a narrowly scoped external runtime message after a successful login, logout, or failed session restoration. The message contains only the event name, never a user ID, access token, refresh token, cookie, or password. `externally_connectable` permits the exact development website origin `http://localhost:5173`; production web origins must be registered explicitly before release.

An event is a revocation/block signal, not proof of identity. A website login marks the extension `sync_required`, blocks all product saves, aborts in-flight save requests where possible, clears cached display identity, and asks the backend to revoke the old extension session. Website logout does the same and shows signed-out state. If revocation fails, the extension keeps the opaque refresh credential only in trusted `chrome.storage.session`, keeps saving blocked, and reports the failure. Reconnection uses the existing interactive Chrome Identity PKCE S256 flow and current BringMyCart website session. The extension verifies the user through authenticated `GET /api/v1/identity/me` and requires the returned `userId` to match the user ID bound to the exchanged extension session before displaying a name or enabling saves. The content script receives only status and never identity or credentials.

Chrome's authorization UI requires user interaction. Therefore an account switch does not silently adopt a new website identity: it fails closed and asks the user to click **Connect current account**, then approve the existing authorization page. This is the secure fallback to account selection; no website cookie or token is copied into extension storage. Cross-tab website login/logout is propagated with `BroadcastChannel`; browser Web Locks serialize rotating website refresh-cookie requests where supported. Neither mechanism shares credentials between tabs.

Registration now requires a full name. Flyway V6 adds nullable `users.full_name VARCHAR(100)` so existing users and records remain valid; new registrations trim and validate the name. `GET /api/v1/identity/me` returns `userId`, email, nullable `fullName`, and role. Older accounts safely fall back to email in the extension. Password and confirmation values remain client-side only except for the actual password in the existing registration request. Sign-in and sign-up password fields have independent accessible visibility controls.

The toolbar popup now shows verified account name/initials, connection/synchronization status, active Amazon product ASIN/canonical link, save feedback, connect/disconnect controls, and dashboard navigation. The existing in-page prompt and saved-product API contract remain in place. `activeTab` was added only to allow the popup to inspect the current page URL after the user opens it; no page scraping, broad host permissions, or retailer credentials are used.

### Verification status for this enhancement

Verification in this environment: frontend tests passed (29 tests) and the production build passed; extension tests passed (20 tests), TypeScript typecheck passed, and both popup/service-worker and content-script production builds passed. Backend tests could not run. Using the existing `C:\Users\Anandh raj\.m2\repository` in offline mode, Maven attempted compilation of 61 production sources, then javac failed while closing `log4j-api-2.24.3.jar` with `java.nio.file.AccessDeniedException` from `WindowsLinkSupport.getRealPath`; test compilation and Testcontainers did not start. No Maven cache was deleted. The project-local `.m2/phase4` cache was inspected and preserved; its Spring Boot parent POM metadata identifies `cached-dependencies` rather than `central`, so Maven cannot resolve it offline under the default repository ID without environment configuration.

Specific manual checks still required in Chrome: confirm `chrome.runtime.sendMessage` from the localhost website reaches the loaded extension; verify account A logout revokes its extension session; sign in as account B, click **Connect current account**, approve PKCE, and verify B's name and product-save ownership; test offline revocation failure and retry; verify popup product display and Amazon prompt updates. No live browser run was performed for this task. A website-to-extension signal is not itself authentication proof; identity is accepted only after PKCE exchange and authenticated profile verification. On a received login/logout event, extension-authenticated operations fail closed until synchronization completes.

## Amazon URL and missed account-event regression fixes

The source review confirmed the URL parser regression: extension recognition required `/dp/{ASIN}` to be the final route and backend validation accepted only bare `/dp/{ASIN}` and `/gp/product/{ASIN}` paths. The backend and extension parsers now accept product-title prefixes before `/dp/{ASIN}`, `/gp/product/{ASIN}`, and `/gp/aw/d/{ASIN}`, plus Amazon referral suffixes beginning `/ref=`. They continue to require HTTPS and exact `amazon.in` or `www.amazon.in` hosts, reject other path suffixes, and canonicalize by the exact uppercase ten-character ASIN. Query/referral parameters are ignored for identity; the original URL remains preserved for the product record and Axesso still receives the canonical URL generated from that ASIN. Existing per-user `(user_id, asin)` deduplication is unchanged.

The account regression was that backend extension sessions were only checked against their own session row. The extension's website lifecycle message was a useful immediate signal but could be missed; in that case an active Account A extension credential remained valid. Website JWTs issued during login/refresh now include their website refresh-token family ID, which is bound to the PKCE authorization code and resulting extension session. The backend requires an active, unexpired website family for authorization, exchange, extension access-token checks, and extension refresh. A logout therefore invalidates the associated extension session even without a browser message. When extension refresh detects that mismatch it revokes the extension session and its refresh tokens. Reconnection still uses the existing interactive PKCE flow; website JWTs/cookies are never copied to the extension.

Flyway V7 adds the family-binding columns and indexes without editing applied migrations. It revokes existing extension sessions that predate this binding. Legacy unbound authorization codes cannot be exchanged. The configured development callback remains tied to `mlogpmpajiokfmjnlmehmpgphbbflpem`; an actual browser `getRedirectURL("bmc-auth")` check still requires rebuilding and reloading the unpacked extension in Chrome.

Runtime inspection also found the immediate lifecycle-message cause in this checkout: `frontend/.env.local` existed but did not define `VITE_BMC_EXTENSION_ID`, so Vite compiled the bridge with no target extension ID and login/logout messages were no-ops. The checked-in `frontend/.env.example` value matches the extension ID previously reported by the developer. The missing value has been added to the ignored local env file and the frontend rebuilt. The Chrome desktop bridge was unavailable for this run, so the currently installed Chrome ID and runtime callback still require comparison in `chrome://extensions` and the service-worker console. For a new or moved unpacked installation, copy `frontend/.env.example` to `frontend/.env.local`, set `VITE_BMC_EXTENSION_ID` to the ID shown by Chrome, and rebuild the frontend; do not use the sample value blindly after the extension ID changes.

Regression tests cover the four supplied URLs, alternate-URL deduplication for one user, the same ASIN independently saved by another user, and rejection of Account A's extension session after website logout or a successful website account switch without relying on a lifecycle message. The verification section below records the checks completed in this environment.

### Latest verification for the URL/account-switch correction

- Frontend tests: 29 passed; production build passed after adding the missing local extension-ID setting.
- Extension tests: 26 passed; TypeScript typecheck passed; popup/background and content-script production builds passed. The generated manifest references existing popup, background, and content outputs and retains only the previously configured permissions/origins.
- Backend tests: not executed. `clean verify` stopped because Windows denied access while Maven removed `backend/target`. A non-clean focused run with the existing Maven repository then reached javac but failed on `AccessDeniedException` for `log4j-api-2.24.3.jar`; a forked compiler attempt encountered the same JDK `WindowsLinkSupport.getRealPath` failure and could not complete test compilation. No Maven cache was removed or changed, and no backend pass is claimed.
- `git diff --check`: pass.
- Chrome runtime verification: unavailable because the Computer Use bridge could not connect to its native pipe. The reported development ID matches `frontend/.env.example` and the added ignored local setting, but the ID shown by the currently installed Chrome extension and `chrome.identity.getRedirectURL("bmc-auth")` still need confirmation in Chrome. Reload the unpacked extension after rebuilding it; rebuild the website after changing its local extension-ID setting.
