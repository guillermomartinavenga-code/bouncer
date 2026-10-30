# Plan de implementación — Bouncer

Este documento es el plan de ejecución concreto para implementar el proyecto descrito en
[`CLAUDE.md`](CLAUDE.md) (la especificación de referencia). Está pensado para que lo revises y
apruebes antes de que empiece la implementación. No se ha escrito código todavía.

Si algo acá propone una decisión técnica no cubierta explícitamente por la especificación, está
marcado como **"a confirmar"** — se puede aceptar la propuesta tal cual o pedir un cambio antes de
arrancar.

---

## 1. Objetivo

Construir, en un monorepo Gradle/Kotlin Multiplatform, un backend Ktor y dos clientes (Android y
Web) que demuestren RBAC, autorización a nivel de objeto, mínimo privilegio y vida limitada de
tokens, con un módulo `shared` reutilizable y una base preparada para un futuro reemplazo del login
por OAuth2/OIDC. El dominio de "orders" es solo el vehículo; no se agrega funcionalidad de negocio
no pedida.

## 2. Layout de módulos propuesto (Gradle)

```
bouncer/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── backend/                         (módulo JVM puro, Ktor server)
│   └── src/main/kotlin/dev/example/bouncer/backend/...
├── shared/                          (módulo KMP: commonMain + androidMain + jvmMain + wasmJsMain/jsMain)
│   └── src/.../dev/example/bouncer/shared/...
├── android/                         (app Android, Compose)
│   └── src/main/kotlin/dev/example/bouncer/android/...
├── web/                             (Compose Multiplatform for Web)
│   └── src/.../dev/example/bouncer/web/...
├── CLAUDE.md
├── PLAN.md
└── README.md
```

**Paquete base:** `dev.example.bouncer` (confirmado).

`shared` declara los targets `jvm` (consumido por `backend`), `android` y el target web elegido en
el spike de la Fase 5 (`wasmJs` o `js`). `backend`, `android` y `web` dependen de `shared`; nunca al
revés.

## 3. Convenciones de nombres (confirmado)

- **Todo en inglés:** entidades de dominio, campos, enums y rutas HTTP. No queda nada en español en
  el código, aunque parte de la conversación de planificación haya sido en español.
  - Entidades: `User`, `Item`, `Order`, `Customer`.
  - Campos: `name`, `price`, `customerId`, `lines`, `quantity`, `status`, `createdAt`.
  - Enum de estado de orden: `PENDING` / `DISPATCHED` / `CANCELLED`. Enum de rol (ya estaba en
    inglés en la especificación): `CUSTOMER` / `DISPATCHER` / `ADMIN`.
  - Rutas: sustantivos en inglés — `/items`, `/orders`, `/customers` (ver sección 5).

## 4. Decisiones técnicas propuestas (a confirmar)

La especificación fija el *qué* (RS256, BCrypt, Koin, kotlinx.serialization, patrón LCE, etc.) pero
no siempre la librería concreta. Propongo:

| Decisión | Propuesta | Motivo |
|---|---|---|
| Motor del servidor Ktor | `ktor-server-netty` | Estándar, bien documentado |
| Librería JWT (RS256 + JWKS) | `com.nimbusds:nimbus-jose-jwt` | Soporte nativo de JWK/JWKS, evita construir el endpoint `/.well-known/jwks.json` a mano |
| Librería BCrypt | `at.favre.lib:bcrypt` | Implementación activa y mantenida |
| Generación de OpenAPI + Swagger UI | `io.github.smiley4:ktor-swagger-ui` | Deriva el spec desde las rutas Ktor (cumple "no YAML a mano"), expone Swagger UI |
| Formato de error HTTP | `{ "error": "<ErrorType>", "message": "<texto>" }`, con `<ErrorType>` igual a los valores de `enum ErrorType` (`UNAUTHORIZED`, `FORBIDDEN`, `NETWORK`, `UNKNOWN`) salvo `NETWORK` que no aplica server-side | Mapeo directo y sin ambigüedad a `UiState.Error` en los clientes |
| Versiones de Kotlin/Ktor/Compose Multiplatform/Koin/kotlinx.* | Últimas estables al momento de implementar, pineadas en `libs.versions.toml` | Pedido explícito del plan |

Si no hay objeciones, estas se toman como definitivas al implementar la Fase 1.

## 5. Decisiones ya confirmadas

1. **`GET /orders/{id}` entre clientes distintos (escenario 2 del guion de demo):** responde **403
   Forbidden** (no 404).
2. **Alcance de gestión de clientes e ítems:** se mantiene deliberadamente mínimo, para no desviar el
   foco de authn/authz. `ADMIN` puede listar clientes (`GET /customers`) y ver sus órdenes a través
   del `GET /orders` existente (que ya devuelve todas las órdenes para ADMIN/DISPATCHER, incluyendo
   `customerId`). No se agrega ABM de clientes (sin `POST/PUT/DELETE /customers`). Los ítems se
   quedan exactamente con el CRUD ya listado en la sección 6 (no se agrega nada adicional — ni acá
   ni en ningún otro lado se amplía alcance de negocio).

## 6. Pantallas por cliente (derivado de la restricción de `client_type`)

Como `DISPATCHER` solo puede loguearse con `client_type=android` y `ADMIN` solo con `web`, las
pantallas quedan así (ningún cliente implementa una pantalla para un rol que no puede loguearse ahí):

- **Android:** login → catálogo de ítems (CUSTOMER + DISPATCHER) → órdenes propias + crear/cancelar
  orden (CUSTOMER) → lista de todas las órdenes + marcar como despachada (DISPATCHER).
- **Web:** login → catálogo de ítems (CUSTOMER + ADMIN) → órdenes propias + crear/cancelar orden
  (CUSTOMER) → CRUD de ítems + todas las órdenes + crear orden a nombre de otro + cancelar
  cualquier orden + listado de clientes (ADMIN).

## 7. Fases de implementación

Cada fase termina con una verificación concreta antes de pasar a la siguiente (tests, levantar el
backend, compilar). No se avanza a la fase N+1 sin confirmar que la fase N quedó verificada.

### Fase 1 — Backend core
- Setup Gradle multi-módulo + `libs.versions.toml`.
- Modelo en memoria (`User`, `Item`, `Order`) + seed al arrancar.
- BCrypt para passwords; generación de par de claves RSA al arrancar (en memoria, no persistida).
- JWT RS256 (access, 5 min) + refresh opaco con rotación (10 min) + endpoint JWKS.
- Dominio: `AuthenticateUserUseCase`, `RefreshSessionUseCase`, `LogoutUseCase`, `PolicyDecisionPoint`.
- `PolicyEnforcementPoint` como plugin/interceptor Ktor; repositorios en memoria sobre interfaces de dominio.
- Endpoints de `/auth/*`, `/me`, `/items`, `/orders`, `/customers` según la tabla de la sección 5 de `CLAUDE.md`.
- OpenAPI autogenerado + Swagger UI.
- **Verificación:** el backend levanta, `GET /.well-known/jwks.json` responde, login + endpoints protegidos funcionan manualmente (curl/Swagger).

### Fase 2 — Tests unitarios
- Test de `PolicyDecisionPoint`/caso de uso para BOLA (CUSTOMER no puede ver/cancelar una orden ajena).
- Test de validación de `client_type` en `AuthenticateUserUseCase` (login rechazado si el rol no corresponde al `client_type`).
- **Verificación:** ambos tests corren y pasan (`./gradlew :shared:test` o el módulo que corresponda).

### Fase 3 — Módulo `shared`
- DTOs (`kotlinx.serialization`) espejo de los endpoints.
- Cliente HTTP (Ktor client) con refresh automático transparente ante 401 por expiración.
- ViewModel base (`CoroutineScope` + `StateFlow`, sin `androidx.lifecycle`).
- `UiState`/`ErrorType` tal como en la sección 6 de `CLAUDE.md`.
- **Verificación:** `shared` compila para los tres targets (jvm, android, web) y el cliente HTTP se puede probar contra el backend de la Fase 1.

### Fase 4 — Android
- Koin (`koin-android`), pantallas de login, catálogo, órdenes (CUSTOMER) y despacho (DISPATCHER) — ver sección 6.
- Guardado del refresh token en Keystore (`minSdk 29`, `setUserAuthenticationRequired(true)`, sin ventana de tiempo) + desbloqueo biométrico antes de usarlo.
- **Verificación:** la app corre en un emulador/dispositivo, login funciona para CUSTOMER y DISPATCHER, el escenario 7 del guion de demo es reproducible.

### Fase 5 — Web
- **Spike primero** (Compose + Koin + cliente Ktor en el target web elegido) — reporto el resultado antes de construir ninguna pantalla.
- Pantallas de login, catálogo, órdenes (CUSTOMER) y administración (ADMIN) — ver sección 6.
- **Verificación:** la app corre en el navegador, login funciona para CUSTOMER y ADMIN.

### Fase 6 — Verificación del guion de demo + README
- Reproducir y documentar los 8 escenarios de la sección 8 de `CLAUDE.md` (ver mapeo abajo).
- Redactar `README.md` raíz con instrucciones para levantar backend, Android y Web, y los pasos exactos de cada escenario.
- **Verificación:** cada uno de los 8 escenarios se ejecuta siguiendo el README tal cual está escrito, sin pasos implícitos.

## 8. Mapeo escenario de demo → cómo se verifica

| # | Escenario | Cómo se reproduce |
|---|---|---|
| 1 | Expiración/refresh de tokens | Login por curl/Swagger o app; esperar >5 min y hacer una request protegida (refresh transparente); esperar >10 min y repetir (debe exigir login) |
| 2 | BOLA en `GET /orders/{id}` | Login como Cliente A, pedir el `id` de una orden de Cliente B (conocido de la seed) |
| 3 | CUSTOMER llama `PATCH /orders/{id}/status` | curl directo con token de CUSTOMER |
| 4 | ADMIN llama `PATCH /orders/{id}/status` | curl directo con token de ADMIN |
| 5 | Login de DISPATCHER con `client_type=web` | curl/Swagger a `/auth/login` con email del DISPATCHER y `client_type: "web"` |
| 6 | Logout revoca refresh | `POST /auth/logout` y luego `POST /auth/refresh` con el mismo token |
| 7 | Sesión Android sin biometría | Guardar sesión, forzar cierre de la app, reabrir e intentar sin pasar el prompt biométrico |
| 8 | JWKS disponible desde el arranque | `GET /.well-known/jwks.json` apenas levanta el backend |

## 9. Checklist de aceptación final

- [ ] Código fuente completo de `backend`, `shared`, `android`, `web`.
- [ ] Tests unitarios de BOLA y de validación de `client_type` (Fase 2).
- [ ] `README.md` raíz con instrucciones de arranque y los 8 escenarios reproducibles paso a paso.
- [ ] Nombres de dominio explícitos (`PolicyEnforcementPoint`, `PolicyDecisionPoint`, los tres casos de uso de auth) presentes en el código, no lógica dispersa sin nombre.
- [ ] Nada de la lista de exclusiones de la sección 1 de `CLAUDE.md` fue implementado.

---

**Para aprobar este plan:** avisame si querés cambiar algo de las secciones 2 o 4 (decisiones
técnicas propuestas). Con tu confirmación arranco la Fase 1.
