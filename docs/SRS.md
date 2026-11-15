# Software Requirements Specification — BringMyCart 2.0

## 1. Document Information and Version History

| Field | Value |
| --- | --- |
| Status | Draft — pending product and architecture approval |
| Version | 0.2 |
| Date | 2026-11-14 |
| Scope | MVP requirements only |
| Owners | BringMyCart product and engineering |

| Version | Date | Change |
| --- | --- | --- |
| 0.1 | 2026-11-14 | Initial requirements baseline |
| 0.2 | 2026-11-14 | Confirmed Java 21, Maven, PostgreSQL, React.js with Vite, Spring Security, and JWT-based authentication |

## 2. Introduction

BringMyCart (BMC) lets users save products they independently find on retailer sites into one account. It is a centralized saved-product platform, not a retailer-cart synchronizer, price-comparison product, checkout service, or payment processor.

## 3. Product Overview, Objectives, and Problem Statement

Users currently retain product choices separately across retailer carts and wishlists. BMC centralizes Amazon India product references in an account, exposes them through a React.js dashboard, and can redirect users to Amazon through an eligible affiliate link. Subject to external verification, qualifying affiliate earnings may yield user reward points redeemable for administrator-provided gift cards. Objectives are a usable centralized collection, secure gift-card fulfillment, and a future-ready shared backend without committing to unverified affiliate incentives.

## 4. Scope and Boundaries

MVP includes Amazon India (`amazon.in`), a React.js customer website and restricted React.js admin area, a Chrome Manifest V3 extension, and one Java/Spring Boot modular-monolith backend. It excludes all retailer-cart integration, Amazon credential collection, payment/checkout, purchase proof from redirects, mobile clients, other retailers, price comparison/history, AI recommendations, automatic retailer matching, and automated gift-card procurement. Android and additional retailers are future scope.

## 5. User Roles and Permissions

| Role | Permissions |
| --- | --- |
| Visitor | Register, sign in, view public installation guidance. |
| Customer | Manage own profile, saved products, points, redemption history, and assigned codes. |
| Administrator | Authorized inventory, redemption monitoring, and approved operational functions only. |
| System | Performs authenticated, audited workflow processing. |

Administrators must not receive broad access by default; authorization is enforced by the backend for every protected operation.

## 6. Functional Requirements

| ID | Title | Role | Priority | Status |
| --- | --- | --- | --- | --- |
| FR-001 | Account access | Visitor, Customer | Must | Confirmed |
| FR-002 | Dashboard and profile | Customer | Must | Confirmed |
| FR-003 | Supported-page detection | Customer | Must | Confirmed |
| FR-004 | Authenticated item save | Customer | Must | Confirmed |
| FR-005 | Saved-product management | Customer | Must | Confirmed |
| FR-006 | Affiliate outbound redirect | Customer | Must | TBD |
| FR-007 | Reward lifecycle | Customer, System | Must | Blocked |
| FR-008 | Gift-card inventory | Administrator | Must | Confirmed |
| FR-009 | Gift-card redemption | Customer, System | Must | Confirmed |
| FR-010 | Administration and audit | Administrator | Must | Confirmed |
| FR-011 | Extension authentication return | Customer | Must | TBD |

### FR-001 — Account Access

**Description / expected behavior:** A visitor can register and sign in to BMC. The React website and Chrome extension use one BringMyCart account system; protected website, extension, and API actions require backend-validated identity and authorization. **Acceptance criteria:** unauthenticated requests cannot access customer or admin data; expired authentication results in reauthentication; administrators require authorized roles. **Dependencies:** authentication/session strategy (TBD). **Status:** Confirmed.

### FR-002 — Dashboard and Profile

**Description / expected behavior:** A signed-in customer can access their profile, centralized saved-product dashboard, reward balance and history, gift-card options, redemption history, securely view previously assigned codes, and extension installation instructions. The official Chrome Web Store URL is not displayed until supplied. **Acceptance criteria:** views contain only the current customer’s data; unavailable rewards are not represented as confirmed. **Dependencies:** FR-001, FR-005, FR-007, FR-009. **Status:** Confirmed.

### FR-003 — Supported-Page Detection

**Description / expected behavior:** The Manifest V3 extension determines whether the active tab is a supported Amazon India product page and exposes Save Item only when appropriate. Available product information may include ASIN, title, original URL, image, and price where legally and technically permitted; no field is guaranteed. **Acceptance criteria:** unsupported retailer pages and invalid product URLs show an explanatory state and do not submit a save; missing product fields are handled without fabricated data. **Dependencies:** approved product URL rules and permitted metadata method (TBD). **Status:** Confirmed.

### FR-004 — Authenticated Item Save

**Description / expected behavior:** An authenticated customer may save the original product URL and permitted available metadata to their BMC collection. Metadata may include ASIN, title, image, price, rating, and review count only where legally and technically permitted, and may be incomplete. Customer review summaries and feedback topics require separate verified provider storage/usage permission before persistence or display. The backend validates identity and submission; the extension never supplies trusted identity alone. **Acceptance criteria:** success is confirmed only after backend acceptance; duplicate saves do not create unwanted duplicates; network/failure messages permit safe retry; original URL and available metadata are retained without fabricating absent fields. **Dependencies:** FR-001, FR-003, metadata design (TBD), verified provider terms for summaries/topics. **Status:** Confirmed; summary/topic storage remains blocked pending verification.

### FR-005 — Saved-Product Management

**Description / expected behavior:** A customer can list and remove their saved products and select a product to buy at Amazon. A product later unavailable on Amazon remains a saved reference unless the customer removes it. **Acceptance criteria:** customers cannot alter other users’ products; removal is reflected in their dashboard; unavailable or incomplete product information is clearly handled. **Dependencies:** FR-004. **Status:** Confirmed.

### FR-006 — Affiliate Outbound Redirect

**Description / expected behavior:** When permitted, BMC prepares an approved affiliate link, records permitted outbound-click information, and redirects the customer to Amazon India. A redirect is not purchase confirmation. **Acceptance criteria:** original product destination is preserved; link-generation or redirect failures are communicated and do not imply a purchase; required affiliate disclosures are presented. **Dependencies:** verified Associates India policies, approved link-generation method, branding/content permission. **Status:** TBD.

### FR-007 — Reward Lifecycle

**Description / expected behavior:** Only reliably confirmed, eligible transactions can produce pending points; points become approved only after settlement checks and may be reversed for cancellations, returns, invalid commissions, or disqualification. Approved points form redeemable balance. **Acceptance criteria:** every change is an auditable ledger event; unconfirmed commission never increases redeemable balance; reversal cannot silently create an inconsistent balance. **Dependencies:** lawful program incentive permission and reliable per-user purchase/commission attribution. **Status:** Blocked.

### FR-008 — Gift-Card Inventory

**Description / expected behavior:** An authorized administrator manually adds legitimate gift-card provider, denomination, encrypted code, validity/expiry when applicable, and metadata. Inventory has unique identity and approved states such as AVAILABLE, RESERVED, ASSIGNED, and DISABLED. **Acceptance criteria:** duplicate uploads are prevented or safely flagged; codes are never exposed to unauthorized users or unnecessary systems; after successful assignment, a code may be securely returned only to its rightful owner and operationally necessary authorized administrators; invalid, expired, already-used, or incorrectly uploaded codes are operable as unavailable and auditable. **Dependencies:** FR-010, encryption/key-management design (TBD). **Status:** Confirmed.

### FR-009 — Gift-Card Redemption

**Description / expected behavior:** A customer selects an available option; BMC atomically verifies eligibility, points, rules, and inventory; reserves/deducts points as approved; assigns exactly one code; and records redemption. Fulfillment is automatic from uploaded inventory, not per-redemption admin approval. **Acceptance criteria:** insufficient points or inventory fails without any points deduction or code assignment; concurrent requests cannot double-spend points or assign one code twice; an idempotent retry returns the original safe outcome rather than creating a second redemption; a failed transaction safely restores or reconciles reservations without duplicate assignment or loss of balance; only the rightful recipient and operationally necessary authorized administrators can securely view an assigned code after successful redemption. Assignment does not prove provider-side card redemption. **Dependencies:** final conversion, denominations, limits, configuration, FR-008. **Status:** Confirmed, with business rules TBD.

### FR-010 — Administration and Audit

**Description / expected behavior:** Authorized administrators manage inventory, view status and redemption transactions, monitor failures, and create audit records for sensitive changes. **Acceptance criteria:** all admin endpoints reject unauthorized access; code values are not unnecessarily returned or logged; audit records identify actor, action, time, and relevant object without secret disclosure. **Dependencies:** FR-001, role model (TBD). **Status:** Confirmed.

### FR-011 — Extension Authentication Return

**Description / expected behavior:** An unauthenticated Save Item attempt shows a sign-in message and directs the user to BMC login. Following secure sign-in, the customer should return to the intended save action where feasible. **Acceptance criteria:** the return flow resists token leakage, spoofing, and unauthorized saves; backend validates the resulting session; failure/expiry prompts safe reauthentication. **Dependencies:** chosen auth/session and extension callback design. **Status:** TBD.

## 7. Non-Functional Requirements

| ID | Requirement | Acceptance Criteria | Status |
| --- | --- | --- | --- |
| NFR-001 | Security | Secrets and codes are never hardcoded, logged, placed in browser storage, or exposed in unauthorized API/error/analytics output. | Confirmed |
| NFR-002 | Privacy | Access is least-privilege; users can access only their own account, saved products, rewards, and assigned codes. | Confirmed |
| NFR-003 | Data consistency | Gift-card assignment, points changes, and redemption records maintain transactional consistency, safe concurrency, idempotency, and partial-failure recovery. | Confirmed |
| NFR-004 | Reliability | Recoverable network interruptions and retries do not duplicate saves, redemptions, ledger events, or inventory assignment. | Confirmed |
| NFR-005 | Observability | Security-relevant administrative and redemption events are auditable without leaking credentials or codes. | Confirmed |
| NFR-006 | Maintainability | Backend remains a modular monolith with documented module boundaries and requirements traceability. | Confirmed |
| NFR-007 | Testability | New behavior has automated tests for normal, validation, authorization, failure, and relevant concurrent/retry paths. | Confirmed |
| NFR-008 | Accessibility | Customer and admin React interfaces follow an accessibility standard selected during design; specific target is TBD. | TBD |
| NFR-009 | Cross-client compatibility | One backend supports website and extension; future Android support must not require a backend rewrite. | Confirmed |
| NFR-010 | Performance, scale, availability | Targets, capacity assumptions, and recovery objectives must be approved before implementation; no numeric target is assumed. | TBD |

## 8. Interface Requirements

### React.js Customer Website

The website supplies account access, customer dashboard, saved-product removal and buy actions, rewards and history, redemption options/history, secure assigned-code access, and extension installation guidance. React.js with Vite is confirmed; supporting libraries are TBD.

### Chrome Extension

The extension uses Chrome Manifest V3, detects supported Amazon India product pages, provides Save Item, reports success/failure/duplicate/unsupported states, directs login when needed, and links to the dashboard. It neither accesses Amazon credentials nor modifies Amazon carts.

### React.js Admin Dashboard

The restricted admin area supports authenticated role-based inventory operations, inventory-state viewing, redemption/failure monitoring, and audited sensitive actions. It must minimize code exposure.

## 9. Authentication, Data, and Security Requirements

The React website and Chrome extension share one backend-authoritative BringMyCart account system. Java 21, Maven, PostgreSQL, Spring Data JPA/Hibernate, Spring Security, and JWT-based authentication are confirmed. The exact supported Spring Boot version, detailed session/token storage and refresh design, login return, and extension callback mechanisms are TBD. Stored gift-card codes require authenticated encryption and key management outside source code; they must never be exposed to unauthorized users or unnecessary systems, though a code may be securely returned to its rightful owner after successful redemption. Sensitive information must be excluded from logs, exceptions, analytics, and client storage. All customer and admin authorization is enforced server-side. Retention, deletion, consent, and privacy-law requirements require later review.

## 10. External Integrations, Compliance, and Dependencies

Amazon Associates India policy, product-content access, permitted affiliate-link generation, disclosures, branding, incentive/cashback/reward restrictions, commission settlement/reversal terms, and any customer-level attribution must be verified from authorized current sources before related functionality is activated. Gift-card sourcing, resale/distribution terms, applicable financial, tax, consumer-protection, and privacy obligations require legal/operational verification. BMC must not infer a purchase from a redirect or infer provider redemption from code display.

## 11. Error Handling and Edge Cases

The system must provide safe, clear outcomes for unauthenticated/expired authentication, failed extension return, unsupported pages, invalid URLs, absent metadata, duplicate saves, unavailable Amazon products, failed link generation or redirects, absent attribution, unconfirmed/reversed commission, insufficient points or inventory, duplicate/concurrent redemption, failed assignment, invalid/expired code, unauthorized admin/code access, partial transaction failures, interruptions, and retries. State-changing retry behavior requires idempotency; exact transport/UI mechanics are design-stage decisions.

## 12. MVP Acceptance Criteria

### Technical MVP Acceptance

The MVP is technically acceptable when a registered authenticated customer can save a supported Amazon India product through the extension, see/remove it in the dashboard, and safely open Amazon; admins can securely upload and manage gift-card inventory; and controlled test balances and controlled inventory can exercise gift-card redemption with atomic safe assignment. Access, audit, encryption, insufficient-inventory handling, concurrency, idempotent retries, failed-transaction recovery, and authorization expectations above must be verified. This technical acceptance does not activate commercial rewards.

### Commercial Rewards Activation

Real affiliate-funded reward issuance remains blocked. It cannot be enabled until Amazon Associates India policy permission for the intended incentive is documented and a reliable, authorized per-user purchase/commission attribution and confirmation mechanism is verified. Affiliate redirects and real reward issuance are excluded from MVP commercial activation until these blockers are resolved.

## 13. Future Enhancements

Potential post-MVP work includes Flipkart, Myntra, Nykaa and other retailer adapters, Android, shared backend client expansion, automated inventory procurement/fulfillment, and additional permitted reward programs. Price comparison/history, AI recommendations, and automatic cross-retailer matching are not current scope.

## 14. Assumptions

Users independently provide/signal Amazon product pages; Amazon India pages can be identified under rules still to be defined; legitimate gift cards can be manually sourced; and a compliant, secure BMC account flow can be designed. These are assumptions, not evidence of Amazon policy permission, attribution availability, or external-provider capability.

## 15. Open Questions and TBD Decisions

- Exact supported Spring Boot version; React supporting libraries; hosting/deployment.
- Authentication/session, extension login return, administrator role/configuration model, and audit retention.
- Product metadata fields/retrieval method, product URL identification rules, and affiliate-link mechanism.
- Points conversion, denominations, limits, reward eligibility, settlement/reversal policy, and gift-card expiry rules.
- Capacity, performance, availability, accessibility, privacy/retention, and recovery targets.
- Chrome Web Store listing URL (do not invent one).

## 16. Technical Risks

Key risks are extension authentication handoff security, incomplete or changing Amazon page metadata, duplicate data, transaction failures across points and inventory, race conditions, encryption/key compromise, and safe code re-display. Mitigate through approved architecture, backend authorization, atomic/idempotent workflows, tests, auditability, and operational procedures—not unapproved infrastructure.

## 17. Business and Compliance Risks

Amazon may prohibit or limit commission sharing, incentives, affiliate-link handling, content/branding usage, or required disclosure practices. Gift-card terms may restrict sourcing, distribution, resale, or expiry treatment. Consumer, tax, financial, and privacy rules may affect rewards. Invalid, expired, used, or mis-uploaded codes impose loss and customer-support risk.

## 18. External Verification Blockers

**BLOCKED / PENDING VERIFICATION:** FR-007 reward issuance and any incentive tied to Amazon affiliate purchases require documented approval that the program permits the intended benefit and a reliable, authorized way to attribute and confirm each eligible customer transaction. No customer-level reporting, webhook, direct purchase API, or commission guarantee may be assumed. FR-006 affiliate redirection remains TBD until approved link generation, disclosures, and permitted product-data/branding use are verified. The centralized-cart MVP may proceed independently; compliant alternatives require product-owner approval.
