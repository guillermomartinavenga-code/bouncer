# bouncer

Demo de Ktor + Kotlin Multiplatform sobre RBAC, autorización a nivel de objeto y ciclo de vida de tokens en Android (con un cliente web).

Es un proyecto de evidencia práctica sobre autenticación y autorización: no es un producto. Los tiempos de vida de los tokens (access 5 min, refresh 10 min) son cortos a propósito para poder demostrar la expiración y el refresh en una demo breve; no son una recomendación para producción.

## Módulos

| Módulo | Qué es |
|---|---|
| `backend` | Servidor Ktor con datos en memoria (JWT RS256, refresh con rotación, JWKS, OpenAPI + Swagger UI) |
| `shared` | Módulo KMP (jvm, android, wasmJs): dominio, DTOs, cliente HTTP con refresh automático y ViewModels |
| `android` | App Android (Compose) con sesión protegida por Keystore + biometría |
| `web` | App web (Compose Multiplatform for Web, target `wasmJs`) |

## Requisitos

- JDK 21.
- Para `android`: Android SDK (platform 37 instalada) y un emulador o dispositivo. Gradle necesita saber dónde está el SDK: IntelliJ crea `local.properties` solo; desde la terminal alcanza con anteponer `ANDROID_HOME=$HOME/Android/Sdk` al comando.
- Para `web`: un navegador con soporte de WasmGC (Chrome/Edge/Firefox recientes).
- `gradle.properties` en la raíz con más memoria para el compilador (sin esto la compilación a Wasm falla con `OutOfMemoryError`):

  ```properties
  org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
  kotlin.daemon.jvmargs=-Xmx3g
  ```

Todos los comandos usan el wrapper de Gradle (`./gradlew`), no hace falta instalar Gradle.

## Cómo levantar el backend

```bash
./gradlew :backend:run
```

Queda escuchando en `http://localhost:8080` (el puerto se puede cambiar con la variable de entorno `PORT`). Cuando el log muestre `Responding at http://0.0.0.0:8080` ya está listo. Para detenerlo, `Ctrl+C`.

- Swagger UI: `http://localhost:8080/swagger`
- Documento OpenAPI (generado desde las rutas): `http://localhost:8080/api.json`
- Clave pública (JWKS): `http://localhost:8080/.well-known/jwks.json`

Los datos viven en memoria: cada reinicio vuelve a sembrar los datos y descarta todas las sesiones.

### Usuarios sembrados

Todos con la contraseña `password123` (solo para la demo).

| Email | Rol | Puede loguearse desde |
|---|---|---|
| `ana@example.com` | CUSTOMER | android, web |
| `beto@example.com` | CUSTOMER | android, web |
| `dani@example.com` | DISPATCHER | android |
| `caro@example.com` | ADMIN | web |

Ejemplo de login con `curl`:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"caro@example.com","password":"password123","client_type":"web"}'
```

## Cómo correr el cliente web

El backend tiene que estar corriendo en `:8080`. En otra terminal:

```bash
./gradlew :web:wasmJsBrowserDevelopmentRun
```

La primera vez tarda (descarga Node/Yarn y compila a WebAssembly). Cuando termine, la app queda en `http://localhost:8081`.

- Los tokens se guardan solo en memoria: recargar la página cierra la sesión.
- El navegador llama al backend desde otro origen, por eso el backend permite CORS únicamente para `localhost:8081` y `127.0.0.1:8081`. Si cambiás el puerto del servidor de desarrollo web, cambialo también en `backend/.../Application.kt`.

## Cómo correr la app Android

1. Levantá el backend (arriba).
2. Abrí un emulador (o conectá un dispositivo) y ejecutá la configuración `android` desde IntelliJ, o por terminal:

   ```bash
   ./gradlew :android:installDebug
   ```

### Backend desde el emulador o desde un teléfono real

La URL del backend se fija al compilar con la propiedad de Gradle `bouncer.backendUrl` (queda en `BuildConfig.BACKEND_BASE_URL`). Por defecto es `http://10.0.2.2:8080`, el alias que usa el **emulador** para llegar a tu máquina; ese alias no existe en un dispositivo físico, donde el login fallaría por no encontrar el servidor.

Para un **dispositivo físico** conectado por USB (con la depuración USB activada) se redirige el puerto del teléfono al de tu máquina con `adb reverse`, y la app apunta a `localhost`, que el `network_security_config.xml` ya permite en claro:

```bash
adb reverse tcp:8080 tcp:8080
./gradlew :android:installDebug -Pbouncer.backendUrl=http://localhost:8080
```

`adb reverse` se pierde al desconectar el cable o reiniciar `adb`; si el login vuelve a fallar, repetí el primer comando. Para volver al emulador, reinstalá sin la propiedad.

No se abre tráfico en claro hacia la red local a propósito: usar la IP de tu máquina por Wi-Fi exigiría permitir HTTP a esa IP en el `network_security_config.xml`.

Para probar la sesión protegida por biometría, el emulador necesita un PIN de pantalla y una huella registrada; sin eso la app funciona pero la sesión no se guarda entre reinicios.

## Logs de tráfico y de tokens

Sirven para ver en vivo qué pasa con la sesión (login, expiración, refresh, re-login).

**Backend** (terminal donde corre `:backend:run`): una línea por request con status, duración y quién la hizo, el motivo de cada rechazo, y los eventos de auth con los vencimientos. El backend nunca imprime tokens, solo los últimos 6 caracteres del refresh token para poder seguir su rotación:

```
login: ana@example.com from client_type=ANDROID
    access token  expires 2026-11-02T14:28:20.542Z
    refresh token …cb1312 expires 2026-11-02T14:33:20.549Z
rejected GET /orders/order-4: FORBIDDEN a CUSTOMER can only view their own orders
403 GET /orders/order-4 (10 ms) user=customer-1 role=CUSTOMER
refresh: token …cb1312 rotated
```

**Clientes**: cada intento HTTP (`-->` / `<--` con status y duración), qué access token se envió y cuántos segundos le quedaban, los tokens completos recibidos en login/refresh (con los claims decodificados) y los eventos de sesión (`session: access token expired or rejected (401), refreshing…`, `refresh rejected … login required`). Cuando vence el access token se ve la secuencia: request con 401 → `POST /auth/refresh` → request repetida con el token nuevo.

- Android: solo en builds debug, en Logcat con el tag `Bouncer`: `adb logcat -s Bouncer` (o el filtro `tag:Bouncer` en el panel Logcat de IntelliJ).
- Web: en la consola del navegador (DevTools), prefijo `[Bouncer]`.

Los clientes imprimen los tokens **completos** (para poder pegarlos en jwt.io o Postman). Es una decisión de demo: cualquiera con acceso a esos logs puede reutilizar los tokens mientras no venzan, por eso en Android el logger no existe en builds release.

## Comandos de Gradle

```bash
# Backend y clientes
./gradlew :backend:run                       # levanta el backend en :8080
./gradlew :web:wasmJsBrowserDevelopmentRun   # levanta el cliente web en :8081
./gradlew :android:installDebug              # compila e instala la app Android en el emulador/dispositivo
./gradlew :android:assembleDebug             # solo compila el APK (android/build/outputs/apk/debug)

# Compilación
./gradlew build                              # compila, testea y corre lint en todos los módulos
./gradlew :backend:build                     # backend (incluye distribuciones)
./gradlew :shared:compileKotlinJvm           # shared para JVM (lo que consume el backend)
./gradlew :shared:compileAndroidMain         # shared para Android
./gradlew :shared:compileKotlinWasmJs        # shared para la web
./gradlew :web:compileKotlinWasmJs           # el cliente web sin levantar el servidor
./gradlew clean                              # borra los directorios build/

# Tests
./gradlew :shared:jvmTest                    # tests unitarios (BOLA, client_type, refresh del cliente HTTP)
./gradlew :shared:jvmTest --tests "*PolicyDecisionPointTest"            # una clase de test
./gradlew :shared:jvmTest --tests "*AuthenticateUserUseCaseTest"        # validación de client_type
./gradlew :shared:jvmTest --tests "*BouncerApiClientTest"               # refresh transparente del token

# Ayuda
./gradlew tasks --all                        # lista todas las tareas disponibles
./gradlew :backend:dependencies              # árbol de dependencias de un módulo
```

Desde la terminal, los comandos que tocan Android o Wasm necesitan `ANDROID_HOME` (ver Requisitos), por ejemplo:

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew build
```

## Estado

Backend, `shared`, app Android y cliente web están implementados. Falta documentar acá, escenario por escenario, los pasos exactos de la demo (expiración y refresh de tokens, BOLA, restricción por `client_type`, revocación en logout, desbloqueo biométrico y JWKS).

La especificación completa está en [`CLAUDE.md`](CLAUDE.md) y el plan de implementación en [`PLAN.md`](PLAN.md).
