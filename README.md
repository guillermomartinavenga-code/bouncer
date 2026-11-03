# bouncer

A Ktor + Kotlin Multiplatform demo of RBAC, object-level authorization and token lifecycle on Android (with a web client).

It is a practical evidence project about authentication and authorization, not a product. The token lifetimes (access 5 min, refresh 10 min) are deliberately short so that expiration and refresh can be shown in a brief demo; they are not a production recommendation.

## Modules

| Module | What it is |
|---|---|
| `backend` | Ktor server with in-memory data (JWT RS256, refresh with rotation, JWKS, OpenAPI + Swagger UI) |
| `shared` | KMP module (jvm, android, wasmJs): domain, DTOs, HTTP client with automatic refresh, and ViewModels |
| `android` | Android app (Compose) with a session protected by Keystore + biometrics |
| `web` | Web app (Compose Multiplatform for Web, `wasmJs` target) |

## Requirements

- JDK 21.
- For `android`: Android SDK (platform 37 installed) and an emulator or device. Gradle needs to know where the SDK is: IntelliJ creates `local.properties` by itself; from a terminal it is enough to prefix the command with `ANDROID_HOME=$HOME/Android/Sdk`.
- For `web`: a browser with WasmGC support (recent Chrome/Edge/Firefox).
- A `gradle.properties` in the repository root that gives the compiler more memory (without it, compiling to Wasm fails with `OutOfMemoryError`):

  ```properties
  org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
  kotlin.daemon.jvmargs=-Xmx3g
  ```

Every command uses the Gradle wrapper (`./gradlew`); there is no need to install Gradle.

## Running the backend

```bash
./gradlew :backend:run
```

It listens on `http://localhost:8080` (the port can be changed with the `PORT` environment variable). It is ready once the log shows `Responding at http://0.0.0.0:8080`. Stop it with `Ctrl+C`.

- Swagger UI: `http://localhost:8080/swagger`
- OpenAPI document (generated from the routes): `http://localhost:8080/api.json`
- Public key (JWKS): `http://localhost:8080/.well-known/jwks.json`

Data lives in memory: every restart re-seeds the data and discards all sessions.

### Seeded users

All use the password `password123` (demo only).

| Email | Role | Can log in from |
|---|---|---|
| `ana@example.com` | CUSTOMER | android, web |
| `beto@example.com` | CUSTOMER | android, web |
| `dani@example.com` | DISPATCHER | android |
| `caro@example.com` | ADMIN | web |

Login example with `curl`:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"caro@example.com","password":"password123","client_type":"web"}'
```

## Running the web client

The backend must be running on `:8080`. In another terminal:

```bash
./gradlew :web:wasmJsBrowserDevelopmentRun
```

The first run is slow (it downloads Node/Yarn and compiles to WebAssembly). When it finishes, the app is at `http://localhost:8081`.

- Tokens are kept in memory only: reloading the page logs the user out.
- The browser calls the backend from another origin, so the backend allows CORS only for `localhost:8081` and `127.0.0.1:8081`. If you change the web dev server's port, change it in `backend/.../Application.kt` as well.

## Running the Android app

1. Start the backend (above).
2. Open an emulator (or connect a device) and run the `android` configuration from IntelliJ, or from a terminal:

   ```bash
   ./gradlew :android:installDebug
   ```

### Reaching the backend from the emulator or a physical phone

The app picks which address to call at startup, depending on where it runs (`android/.../BackendUrl.kt`):

| Where it runs | URL it uses | What you have to do |
|---|---|---|
| Emulator | `http://10.0.2.2:8080` (the emulator's alias for your machine) | Nothing |
| Physical device | `http://localhost:8080` | `adb reverse tcp:8080 tcp:8080` |

It works the same whether you install from the IDE's Run button or from a terminal: no parameter is needed. In a debug build, the first line of the log (`adb logcat -s Bouncer`) tells you which URL ended up in use: `backend: http://...`.

For a **physical device** connected over USB (with USB debugging enabled):

```bash
adb reverse tcp:8080 tcp:8080
```

`adb reverse` forwards the phone's port 8080 to your machine's, so the phone's `localhost` reaches the backend. That forwarding is **lost** when you reboot the machine, unplug the cable or restart `adb`: if login stops working, run the command again. You can check that it is active with `adb reverse --list`. `localhost` is already allowed in cleartext in `network_security_config.xml`, so no more HTTP traffic than necessary is opened.

If you need a different address (for example your machine's Wi-Fi IP), you can force it at build time:

```bash
./gradlew :android:installDebug -Pbouncer.backendUrl=http://192.168.1.5:8080
```

That replaces the automatic detection, but it also requires allowing cleartext HTTP to that IP in `network_security_config.xml`; for that reason it is not the recommended route.

To try the biometric-protected session, the emulator needs a screen lock (PIN or pattern) and an enrolled fingerprint (a virtual one: use *Extended controls → Fingerprint → Touch the sensor*); without them the app works but the session is not kept across restarts.

## Traffic and token logs

They let you watch what happens to the session as it happens (login, expiration, refresh, re-login).

**Backend** (the terminal where `:backend:run` runs): one line per request with status, duration and who made it, the reason for every rejection, and the auth events with their expiry times. The backend never prints tokens, only the last 6 characters of the refresh token so its rotation can be followed:

```
login: ana@example.com from client_type=ANDROID
    access token  expires 2026-11-02T14:28:20.542Z
    refresh token …cb1312 expires 2026-11-02T14:33:20.549Z
rejected GET /orders/order-4: FORBIDDEN a CUSTOMER can only view their own orders
403 GET /orders/order-4 (10 ms) user=customer-1 role=CUSTOMER
refresh: token …cb1312 rotated
```

**Clients**: every HTTP attempt (`-->` / `<--` with status and duration), which access token was sent and how many seconds it had left, the full tokens received on login/refresh (with the decoded claims), and the session events (`session: access token expired or rejected (401), refreshing…`, `refresh rejected … login required`). When the access token expires you see the sequence: request with a 401 → `POST /auth/refresh` → request repeated with the new token.

- Android: debug builds only, in Logcat with the tag `Bouncer`: `adb logcat -s Bouncer` (or the `tag:Bouncer` filter in IntelliJ's Logcat panel).
- Web: in the browser console (DevTools), prefixed with `[Bouncer]`.

The clients print the **full** tokens (so they can be pasted into jwt.io or Postman). This is a demo decision: anyone with access to those logs can reuse the tokens until they expire, which is why on Android the logger does not exist in release builds.

## Gradle commands

```bash
# Backend and clients
./gradlew :backend:run                       # start the backend on :8080
./gradlew :web:wasmJsBrowserDevelopmentRun   # start the web client on :8081
./gradlew :android:installDebug              # build and install the Android app on the emulator/device
./gradlew :android:assembleDebug             # build the APK only (android/build/outputs/apk/debug)

# Building
./gradlew build                              # compile, test and lint every module
./gradlew :backend:build                     # backend (includes distributions)
./gradlew :shared:compileKotlinJvm           # shared for the JVM (what the backend consumes)
./gradlew :shared:compileAndroidMain         # shared for Android
./gradlew :shared:compileKotlinWasmJs        # shared for the web
./gradlew :web:compileKotlinWasmJs           # the web client without starting the server
./gradlew clean                              # delete the build/ directories

# Tests
./gradlew :shared:jvmTest                    # unit tests (BOLA, client_type, HTTP client refresh)
./gradlew :shared:jvmTest --tests "*PolicyDecisionPointTest"            # one test class
./gradlew :shared:jvmTest --tests "*AuthenticateUserUseCaseTest"        # client_type validation
./gradlew :shared:jvmTest --tests "*BouncerApiClientTest"               # transparent token refresh

# Help
./gradlew tasks --all                        # list every available task
./gradlew :backend:dependencies              # a module's dependency tree
```

From a terminal, the commands that touch Android or Wasm need `ANDROID_HOME` (see Requirements), for example:

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew build
```

## Status

The backend, `shared`, the Android app and the web client are implemented, and the 8 demo script scenarios (token expiration and refresh, BOLA, `client_type` restriction, revocation on logout, biometric unlock and JWKS) have been verified.

The full specification is in [`CLAUDE.md`](CLAUDE.md) and the implementation plan is in [`PLAN.md`](PLAN.md).
