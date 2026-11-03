# Implementation plan — Bouncer

This document is the concrete execution plan for implementing the project described in
[`CLAUDE.md`](CLAUDE.md) (the reference specification). It was reviewed and approved before
implementation started, and the text below is kept as it was approved.

If anything here proposes a technical decision that the specification does not explicitly cover, it
is marked **"to be confirmed"** — those proposals were accepted or adjusted before the phase that
needed them.

## Status

| Phase | Status |
|---|---|
| 1 — Backend core | Done |
| 2 — Unit tests | Done |
| 3 — `shared` module | Done |
| 4 — Android | Done |
| 5 — Web | Done |
| 6 — Demo script verification + README | Done |

All six phases are complete: the implementation and the 8 demo script scenarios have been verified.

---

## 1. Goal

Build, in a Gradle/Kotlin Multiplatform monorepo, a Ktor backend and two clients (Android and Web)
that demonstrate RBAC, object-level authorization, least privilege and short-lived tokens, with a
reusable `shared` module and a base prepared for a future replacement of the login with
OAuth2/OIDC. The "orders" domain is only the vehicle; no business functionality beyond what was
asked for is added.

## 2. Proposed module layout (Gradle)

```
bouncer/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── backend/                         (pure JVM module, Ktor server)
│   └── src/main/kotlin/dev/example/bouncer/backend/...
├── shared/                          (KMP module: commonMain + androidMain + jvmMain + wasmJsMain/jsMain)
│   └── src/.../dev/example/bouncer/shared/...
├── android/                         (Android app, Compose)
│   └── src/main/kotlin/dev/example/bouncer/android/...
├── web/                             (Compose Multiplatform for Web)
│   └── src/.../dev/example/bouncer/web/...
├── CLAUDE.md
├── PLAN.md
└── README.md
```

**Base package:** `dev.example.bouncer` (confirmed).

`shared` declares the `jvm` target (consumed by `backend`), `android`, and the web target chosen in
the Phase 5 spike (`wasmJs` or `js`). `backend`, `android` and `web` depend on `shared`; never the
other way around.

## 3. Naming conventions (confirmed)

- **Everything in English:** domain entities, fields, enums and HTTP routes. Nothing is left in
  Spanish in the code, even though part of the planning conversation was in Spanish.
  - Entities: `User`, `Item`, `Order`, `Customer`.
  - Fields: `name`, `price`, `customerId`, `lines`, `quantity`, `status`, `createdAt`.
  - Order status enum: `PENDING` / `DISPATCHED` / `CANCELLED`. Role enum (already in English in
    the specification): `CUSTOMER` / `DISPATCHER` / `ADMIN`.
  - Routes: English nouns — `/items`, `/orders`, `/customers` (see section 5).

## 4. Proposed technical decisions (to be confirmed)

The specification fixes the *what* (RS256, BCrypt, Koin, kotlinx.serialization, the LCE pattern,
etc.) but not always the concrete library. Proposal:

| Decision | Proposal | Reason |
|---|---|---|
| Ktor server engine | `ktor-server-netty` | Standard, well documented |
| JWT library (RS256 + JWKS) | `com.nimbusds:nimbus-jose-jwt` | Native JWK/JWKS support, avoids building the `/.well-known/jwks.json` endpoint by hand |
| BCrypt library | `at.favre.lib:bcrypt` | Actively maintained implementation |
| OpenAPI generation + Swagger UI | `io.github.smiley4:ktor-swagger-ui` | Derives the spec from the Ktor routes (meets "no hand-written YAML"), exposes Swagger UI |
| HTTP error format | `{ "error": "<ErrorType>", "message": "<text>" }`, with `<ErrorType>` equal to the values of `enum ErrorType` (`UNAUTHORIZED`, `FORBIDDEN`, `NETWORK`, `UNKNOWN`) except `NETWORK`, which does not apply server-side | Direct, unambiguous mapping to `UiState.Error` in the clients |
| Kotlin/Ktor/Compose Multiplatform/Koin/kotlinx.* versions | Latest stable at implementation time, pinned in `libs.versions.toml` | Explicit request in the plan |

If there are no objections, these are taken as final when implementing Phase 1.

## 5. Decisions already confirmed

1. **`GET /orders/{id}` across different customers (demo script scenario 2):** responds **403
   Forbidden** (not 404).
2. **Scope of customer and item management:** deliberately kept minimal, so as not to divert the
   focus from authn/authz. `ADMIN` can list customers (`GET /customers`) and see their orders
   through the existing `GET /orders` (which already returns every order for ADMIN/DISPATCHER,
   including `customerId`). No customer CRUD is added (no `POST/PUT/DELETE /customers`). Items stay
   with exactly the CRUD already listed in section 6 (nothing additional is added — neither here
   nor anywhere else is the business scope widened).

## 6. Screens per client (derived from the `client_type` restriction)

Since `DISPATCHER` can only log in with `client_type=android` and `ADMIN` only with `web`, the
screens are as follows (no client implements a screen for a role that cannot log in there):

- **Android:** login → item catalog (CUSTOMER + DISPATCHER) → own orders + create/cancel order
  (CUSTOMER) → list of all orders + mark as dispatched (DISPATCHER).
- **Web:** login → item catalog (CUSTOMER + ADMIN) → own orders + create/cancel order (CUSTOMER) →
  item CRUD + all orders + create an order on behalf of someone else + cancel any order + customer
  list (ADMIN).

## 7. Implementation phases

Each phase ends with a concrete verification before moving on to the next (tests, starting the
backend, compiling). Phase N+1 does not start without confirming that phase N was verified.

### Phase 1 — Backend core
- Multi-module Gradle setup + `libs.versions.toml`.
- In-memory model (`User`, `Item`, `Order`) + seed at startup.
- BCrypt for passwords; RSA key pair generated at startup (in memory, not persisted).
- JWT RS256 (access, 5 min) + opaque refresh token with rotation (10 min) + JWKS endpoint.
- Domain: `AuthenticateUserUseCase`, `RefreshSessionUseCase`, `LogoutUseCase`, `PolicyDecisionPoint`.
- `PolicyEnforcementPoint` as a Ktor plugin/interceptor; in-memory repositories over domain interfaces.
- `/auth/*`, `/me`, `/items`, `/orders`, `/customers` endpoints according to the table in section 5 of `CLAUDE.md`.
- Auto-generated OpenAPI + Swagger UI.
- **Verification:** the backend starts, `GET /.well-known/jwks.json` responds, login + protected endpoints work manually (curl/Swagger).

### Phase 2 — Unit tests
- Test of `PolicyDecisionPoint`/use case for BOLA (a CUSTOMER cannot view/cancel someone else's order).
- Test of `client_type` validation in `AuthenticateUserUseCase` (login rejected if the role does not match the `client_type`).
- **Verification:** both tests run and pass (`./gradlew :shared:test` or the relevant module).

### Phase 3 — `shared` module
- DTOs (`kotlinx.serialization`) mirroring the endpoints.
- HTTP client (Ktor client) with transparent automatic refresh on a 401 caused by expiration.
- Base ViewModel (`CoroutineScope` + `StateFlow`, no `androidx.lifecycle`).
- `UiState`/`ErrorType` exactly as in section 6 of `CLAUDE.md`.
- **Verification:** `shared` compiles for the three targets (jvm, android, web) and the HTTP client can be tried against the Phase 1 backend.

### Phase 4 — Android
- Koin (`koin-android`), login, catalog, orders (CUSTOMER) and dispatch (DISPATCHER) screens — see section 6.
- Refresh token stored in the Keystore (`minSdk 29`, `setUserAuthenticationRequired(true)`, no time window) + biometric unlock before using it.
- **Verification:** the app runs on an emulator/device, login works for CUSTOMER and DISPATCHER, demo script scenario 7 is reproducible.

### Phase 5 — Web
- **Spike first** (Compose + Koin + Ktor client on the chosen web target) — I report the result before building any screen.
- Login, catalog, orders (CUSTOMER) and administration (ADMIN) screens — see section 6.
- **Verification:** the app runs in the browser, login works for CUSTOMER and ADMIN.

### Phase 6 — Demo script verification + README
- Reproduce and document the 8 scenarios of section 8 of `CLAUDE.md` (see the mapping below).
- Write the root `README.md` with instructions to start the backend, Android and Web, and the exact steps of each scenario.
- **Verification:** each of the 8 scenarios is executed following the README exactly as written, with no implicit steps.

## 8. Demo scenario → how it is verified

| # | Scenario | How it is reproduced |
|---|---|---|
| 1 | Token expiration/refresh | Log in via curl/Swagger or the app; wait >5 min and make a protected request (transparent refresh); wait >10 min and repeat (must require login) |
| 2 | BOLA on `GET /orders/{id}` | Log in as Customer A, request the `id` of one of Customer B's orders (known from the seed) |
| 3 | CUSTOMER calls `PATCH /orders/{id}/status` | Direct curl with a CUSTOMER token |
| 4 | ADMIN calls `PATCH /orders/{id}/status` | Direct curl with an ADMIN token |
| 5 | DISPATCHER login with `client_type=web` | curl/Swagger to `/auth/login` with the DISPATCHER's email and `client_type: "web"` |
| 6 | Logout revokes refresh | `POST /auth/logout` and then `POST /auth/refresh` with the same token |
| 7 | Android session without biometrics | Save a session, force-close the app, reopen it and try without passing the biometric prompt |
| 8 | JWKS available from startup | `GET /.well-known/jwks.json` right after the backend starts |

## 9. Final acceptance checklist

- [ ] Complete source code for `backend`, `shared`, `android`, `web`.
- [ ] Unit tests for BOLA and `client_type` validation (Phase 2).
- [ ] Root `README.md` with startup instructions and the 8 scenarios reproducible step by step.
- [ ] Explicit domain names (`PolicyEnforcementPoint`, `PolicyDecisionPoint`, the three auth use cases) present in the code, not unnamed scattered logic.
- [ ] Nothing from the exclusion list in section 1 of `CLAUDE.md` was implemented.

---

**To approve this plan:** let me know if you want to change anything in sections 2 or 4 (proposed
technical decisions). With your confirmation I start Phase 1.
