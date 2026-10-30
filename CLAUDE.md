# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Bouncer is a practical evidence project for an engineering objective on **authentication and
authorization on Android**. It is not meant to be a complete product — its only purpose is to
demonstrate, with running code, understanding of RBAC, object-level authorization, least privilege,
short-lived tokens, and "never trust the client." It also doubles as a reusable base for a *future*
objective on OAuth 2.0 / OpenID Connect, which is **not** implemented in this project.

The "orders" domain (`Item`, `Order`, `Customer`) is a vehicle for demonstrating security concepts,
not a goal in itself. Do not add business functionality beyond what is specified below.

## Current state

Phases 1–4 of `PLAN.md` are implemented (backend, shared, tests, Android app). Still to do: the web
app (Phase 5) and the README with the demo-script walkthrough (Phase 6). The plan below remains the
reference spec.

Commands (Gradle wrapper; Android builds need `ANDROID_HOME=$HOME/Android/Sdk`, or a
`local.properties` with `sdk.dir`, which IntelliJ creates and git ignores):

```bash
./gradlew :backend:run                 # backend on http://localhost:8080 (Swagger UI at /swagger)
./gradlew :shared:jvmTest              # unit tests (BOLA, client_type, HTTP-client refresh)
./gradlew :shared:jvmTest --tests "*PolicyDecisionPointTest"   # a single test class
./gradlew :android:assembleDebug       # Android APK; the emulator reaches the backend at 10.0.2.2:8080
./gradlew build                        # everything
```

Layout notes that aren't obvious from a single file:
- Domain, DTOs, `BouncerApiClient` (Ktor client with transparent token refresh) and the concrete
  ViewModels all live in `shared/commonMain`; `backend` and `android` only add adapters
  (in-memory repos/JWT/BCrypt, and Keystore + biometric token storage + Compose UI respectively).
- Android ViewModels are plain objects owned by Compose (`rememberViewModel`), not androidx
  ViewModels; the activity opts out of recreation via `configChanges` for that reason.
- The Android app keeps only the refresh token on disk, encrypted with an auth-required Keystore
  RSA key: encrypting uses the public key (no prompt), decrypting needs a biometric prompt.
  Testing it on an emulator requires a screen lock (PIN) and an enrolled fingerprint.

## Scope

**In scope:** Ktor backend with in-memory data (clients/users, items, orders, auth), a KMP shared
module, an Android app (Compose), a web app (Compose Multiplatform for Web), 4 hardcoded users, and
a verified demo script.

**Explicitly out of scope — do not implement even if it looks easy, this is a scope decision, not a
technical limitation:** OAuth 2.0/OIDC, real Play Integrity, real TLS, CI/CD, real persistence
(database), OpenAPI-to-client code generation, exhaustive test coverage (only the two use cases
specified below), pagination, i18n, polished visual design.

## How to work in this repo

- Ask before making design decisions the plan below doesn't explicitly cover — do not invent
  additional scope.
- Follow the work phases in order (see "Suggested work phases"); it's fine to pause and verify each
  phase before moving to the next rather than doing everything in one long session.
- For the web app (Compose Multiplatform), do the Compose + Koin + Ktor-client-on-web spike first
  and confirm the result before building any screens.
- At the end of each phase, run whatever can be run (tests, start the backend, confirm it compiles)
  before declaring that phase done.
- Code must be self-explanatory as *evidence of understanding*, not just working code: use class
  names that reflect the plan's concepts verbatim (e.g. `PolicyEnforcementPoint`,
  `PolicyDecisionPoint`, `AuthenticateUserUseCase`), never a generic unnamed middleware/if-chain.

## Architecture (monorepo)

```
/backend   → Ktor server (Clean Architecture / Ports & Adapters)
/shared    → KMP module: domain (entities + use cases), UI state (LCE), DTOs, HTTP client
/android   → Android app: presentation (Compose + MVVM + LCE) over /shared's domain
/web       → Web app (Compose Multiplatform for Web): presentation (Compose + MVVM + LCE), same domain
```

`shared` concentrates domain logic, the base ViewModel, and UI state modeling (LCE) so Android and
Web run identical business logic and model screen state identically. `backend`, `android`, and
`web` all depend on `shared`; `shared` never depends on them.

## Naming conventions

- **Base package:** `dev.example.bouncer` (e.g. `dev.example.bouncer.shared.domain`,
  `dev.example.bouncer.backend.routes`).
- Everything is named in English — domain entities, fields, enums, and HTTP routes alike (e.g.
  `User`, `Order`, `Customer`, `status`, `createdAt`, `PENDING`/`DISPATCHED`/`CANCELLED`, `/orders`,
  `/customers`). There is no Spanish naming anywhere in the codebase, even though some of the
  original planning conversation was in Spanish.

## Data model (in-memory, seeded on backend startup)

| Entity | Main fields |
|---|---|
| **User** | id, name, email, passwordHash (BCrypt), role (`CUSTOMER` / `DISPATCHER` / `ADMIN`) |
| **Item** | id, name, price, stock |
| **Order** | id, customerId, lines (itemId + quantity), status (`PENDING` / `DISPATCHED` / `CANCELLED`), createdAt |

Seed: 2 `CUSTOMER`, 1 `ADMIN`, 1 `DISPATCHER`; 4–5 `Item`; 2–3 `Order` per customer (at least one
`PENDING` each). Use in-memory structures (`ConcurrentHashMap` or equivalent) — no database.

## Role/permission matrix (non-negotiable without confirming first)

| Action | CUSTOMER | DISPATCHER | ADMIN |
|---|---|---|---|
| Login from `client_type=android` | ✅ | ✅ | ❌ |
| Login from `client_type=web` | ✅ | ❌ | ✅ |
| View item catalog | ✅ | ✅ | ✅ |
| Create/edit/delete items | ❌ | ❌ | ✅ |
| Create own order | ✅ | ❌ | ✅ (on behalf of anyone) |
| View own orders | ✅ (own only) | ❌ | — |
| View all orders | ❌ | ✅ | ✅ |
| Mark order as dispatched | ❌ | **✅ exclusive** | ❌ |
| Cancel own order (if pending) | ✅ | ❌ | ✅ (any) |
| List all customers | ❌ | ❌ | ✅ |

The two most important authorization rules must be explicit, testable domain logic — not a
scattered `if` in a route:

- **Object-level authorization:** a `CUSTOMER` can only view/cancel their own orders, never another
  customer's, even if they know the id.
- **`client_type` login restriction:** the server rejects a login whose user role doesn't match the
  declared `client_type` (per the table above), regardless of what screens exist in either client app.

Customer and item management stay deliberately minimal: `ADMIN` can list customers (`GET
/customers`) and see their orders via the existing `GET /orders`, and items only get the CRUD
already listed below. Do not add customer CRUD (create/update/delete) or any other admin management
screen beyond what's explicitly listed — this is a scope decision to keep the project focused on
authn/authz, not a gap to fill in.

## Authentication & authorization (backend)

**Tokens:**
- Access token: JWT signed with **RS256**, **5 min** lifetime. Claims: `sub` (userId), `role`,
  `iat`, `exp`, `iss`.
- Refresh token: opaque value, **10 min** lifetime, stored server-side in memory with **rotation**
  (each use invalidates the previous one and issues a new one).
- `GET /.well-known/jwks.json` exposes the RSA public key generated at startup.
- These lifetimes are deliberately short (to demo expiration/refresh quickly) — document this in
  the README as a demo choice, not a production recommendation.

**Auth endpoints:**

| Endpoint | Behavior |
|---|---|
| `POST /auth/login` | `{ email, password, client_type }` → validates credentials (BCrypt) + role allowed for that `client_type` → issues access + refresh token |
| `POST /auth/refresh` | Validates and rotates the refresh token, issues a new access token |
| `POST /auth/logout` | Revokes the received refresh token |
| `GET /.well-known/jwks.json` | Public key to verify JWTs |
| `GET /me` | Authenticated user's data |

**Authorization layer (name these exactly):**
- Domain (`shared`): use cases `AuthenticateUserUseCase`, `RefreshSessionUseCase`,
  `LogoutUseCase`; and `PolicyDecisionPoint` as a pure domain function (role + action + resource
  owner → allow/deny), with no Ktor dependency, testable in isolation.
- Backend: Ktor routes + `PolicyEnforcementPoint` (interceptor/plugin) that resolves the
  authenticated principal and calls `PolicyDecisionPoint` before running the handler; in-memory
  repositories implementing domain-defined interfaces.

**Business endpoints:**
```
GET    /items
POST   /items                  (ADMIN)
PUT    /items/{id}             (ADMIN)
DELETE /items/{id}             (ADMIN)

GET    /orders                 (ADMIN, DISPATCHER: all | CUSTOMER: own only)
GET    /orders/{id}            (ownership check if role is CUSTOMER)
POST   /orders                 (CUSTOMER: own | ADMIN: on behalf of anyone)
PATCH  /orders/{id}/status     (DISPATCHER exclusively)
DELETE /orders/{id}            (CUSTOMER: own + pending | ADMIN: any)

GET    /customers              (ADMIN)
```

## Cross-cutting technical decisions

- **DI:** Koin across all three runnable modules (`koin-ktor` in backend, `koin-android` in
  Android; confirm support on the chosen web target during the spike). Pin versions in a Gradle
  version catalog (`libs.versions.toml`), using the latest stable releases.
- **Android minSdk:** 29. The Keystore key protecting the refresh token must require
  authentication on every use (`setUserAuthenticationRequired(true)`, no time-validity window —
  that variant needs API 30).
- **Clean Architecture** in all three executables, same vocabulary: domain (entities + use cases,
  in `shared`) → data (repositories/adapters) → presentation/entry point (Ktor routes in backend;
  ViewModel + Compose in Android/Web).
- **MVVM** in Android and Web: a lightweight ViewModel defined in `shared` (plain class with
  `CoroutineScope` + `StateFlow`, **no** multiplatform `androidx.lifecycle` dependency) invoking
  domain use cases. Backend does not use MVVM.
- **Serialization:** `kotlinx.serialization` for shared DTOs.
- **OpenAPI/Swagger:** generate the OpenAPI document from Ktor routes via a plugin that derives it
  automatically (not a hand-maintained YAML), and expose Swagger UI. Use it as documentation and to
  verify the demo scenarios. **Do not generate client code or DTOs from this spec** — `shared` is
  hand-written.
- **LCE pattern** in `shared`, consumed identically by Android and Web:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val type: ErrorType, val message: String) : UiState<Nothing>
}

enum class ErrorType { UNAUTHORIZED, FORBIDDEN, NETWORK, UNKNOWN }
```

  Exactly these three states (an empty list is `Content(emptyList())`, not a fourth state). A 401
  from access-token expiry is resolved transparently (HTTP client refreshes and retries) without
  reaching the ViewModel as an error; a 403, or a 401 that persists after refreshing, must
  propagate as `UiState.Error` to the screen.

## Designed for reuse by a future OAuth/OIDC objective (do not implement now)

- The resource server validates tokens by signature against a public key (JWKS), never by logic
  coupled to how the token was issued.
- The login flow is isolated under `/auth/*` so it can later be replaced by a redirect flow to an
  external Authorization Server (with PKCE) without touching `/items` or `/orders`.
- The shared HTTP client separates "valid token" from "how it was obtained," so a different login
  provider can be added later without rewriting that layer.

## Decisions confirmed

- Cross-customer object-level access (`GET /orders/{id}` on someone else's order) returns **403
  Forbidden**, not 404.
- Customer and item management are deliberately minimal (see the note under the role/permission
  matrix) — no customer CRUD beyond `GET /customers`.

## Demo script (acceptance criteria — document how to reproduce each in the README)

| # | Scenario | Expected result |
|---|---|---|
| 1 | Login; access token expires at 5 min and auto-refreshes; refresh token expires at 10 min and forces a new login | Transparent retry within the 10 min window; outside it, login is required again |
| 2 | Client A requests `GET /orders/{id}` of a Client B order | 403 (chosen over 404 — see "Decisions confirmed" below); in-app `UiState.Error(FORBIDDEN)` |
| 3 | With a `CUSTOMER` token, call `PATCH /orders/{id}/status` directly (no screen exposes this) | 403 |
| 4 | With an `ADMIN` token, call `PATCH /orders/{id}/status` | 403 (only `DISPATCHER` can) |
| 5 | Login with the `DISPATCHER`'s email but `client_type: "web"` | 403/401 at login, before any token is issued |
| 6 | `POST /auth/logout` with a refresh token, then `POST /auth/refresh` with that same token | Refresh fails (token revoked) |
| 7 | On Android, access the saved session without passing biometric unlock | Saved token unusable; usable after unlock |
| 8 | `GET /.well-known/jwks.json` | Returns the current public key, available since backend startup |

## Suggested work phases

1. Backend core: in-memory model + seed, BCrypt, JWT RS256 + JWKS, domain with use cases +
   `PolicyDecisionPoint`, `PolicyEnforcementPoint`, protected endpoints, refresh/logout, OpenAPI +
   Swagger UI.
2. Unit tests for the BOLA use case and the `client_type` validation use case.
3. `shared` module: DTOs, domain, HTTP client with automatic refresh, base ViewModel, `UiState`.
4. Android: Koin, login, biometrics + Keystore, customer and dispatcher screens.
5. Web: spike (Compose + Koin + Ktor client on the chosen target), login, customer and admin screens.
6. Verify the 8 demo scenarios and write the README.

## Deliverable checklist

- Full, working source for all four modules (`backend`, `shared`, `android`, `web`).
- Unit tests for the two use cases above (BOLA, `client_type` validation).
- Root `README.md` covering: how to run the backend, the Android app, and the web app; and, per
  demo scenario, the exact reproduction steps (curl command or Swagger UI, and/or app action) and
  the expected result.
