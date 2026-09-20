# Plan: migrar Caja a Neon Auth (nativo)

Estado: **en ejecución**. Cubre los dos repositorios: la web
(`F:\Projects\fantastic-eureka`) y esta app Android.

Neon Auth ya está habilitado en el proyecto Neon de los datos (`ep-dark-moon`):

- Base URL: `https://ep-dark-moon-aeazylbr.neonauth.c-2.us-east-2.aws.neon.tech/neondb/auth`
- JWKS: `<base>/.well-known/jwks.json` (firmas **EdDSA/Ed25519**; `iss` y
  `aud` = origen de la base URL)

Integración: SDK `@neondatabase/auth` (+ `@neondatabase/auth-ui`). Variables:
`NEON_AUTH_BASE_URL` + `NEON_AUTH_COOKIE_SECRET` (aleatorio ≥32 chars,
generado localmente; no sale de `.env.local`). No hay claves de terceros: el
servidor de auth lo sirve Neon desde el dominio del propio proyecto.

## Requisito innegociable: la cuenta sigue siendo opcional

La app debe seguir funcionando entera **sin autenticarse** (estado `Guest`:
todo vive en Room, en el dispositivo). Neon Auth solo entra en juego al
**conectar una cuenta** para respaldo y sincronización — igual que el auth
actual. En concreto se conserva:

- Arranque sin login: `CajaApp` no exige sesión; `AuthState.Guest` es un
  estado normal, no un error.
- `GuestMigration`: al conectar cuenta por primera vez, el outbox del modo
  invitado se reescribe tras el bootstrap y se sube. El disparo es el mismo
  (primer login → vínculo/provisión del `User` → bootstrap → migración).
- Sesión caducada ≠ datos perdidos: `SessionExpired` solo pausa la
  sincronización; Room y el outbox se conservan.

## Qué se reemplaza

| Superficie | Hoy | Con Neon Auth |
|---|---|---|
| Web | cookie `caja_session`; token opaco (SHA-256 en tabla `Session`); scrypt en `User.passwordHash` | Sesión de Neon Auth (`auth.getSession()`, cookie propia del SDK) |
| API móvil | Bearer opaco propio, renovación deslizante 30/15 días | JWT de Neon Auth verificado con `jose` contra el JWKS (EdDSA) |
| Android (modo API) | `auth/login\|register` de `/api/mobile/v1` | Endpoints REST de la base URL de Neon Auth, directo desde la app |
| Android (modo Neon) | scrypt local (`NodeScrypt.kt`) contra `passwordHash` | Login contra la base URL de Neon Auth; los datos siguen por SQL |

Identidad: `User.id` (cuid) sigue siendo la clave multi-tenant de las 12
tablas. Se añade **`User.authUserId`** (id del usuario en Neon Auth, único,
opcional) como puente.

## Vínculo de usuarios existentes: por email, sin script

En vez de un script de migración: al resolver sesión, si el usuario de Neon
Auth no está vinculado, se busca `User` por **email**:

- existe y `authUserId IS NULL` → se vincula (sus datos aparecen tal cual);
- no existe → provisión JIT: `User` nuevo + `bootstrapUserDefaults` en una
  transacción (respetando `ALLOW_REGISTRATION`).

Las contraseñas scrypt no se migran: cada usuario fija una nueva al
registrarse en Neon Auth **con su mismo email**. `passwordHash` pasa a
opcional y muere en la limpieza final.

## Fase 1 — Web (en curso)

1. `pnpm add @neondatabase/auth @neondatabase/auth-ui jose`.
2. `NEON_AUTH_BASE_URL` + `NEON_AUTH_COOKIE_SECRET` en `.env.local` /
   `.env.example`.
3. `src/lib/auth/neon.ts`: `createNeonAuth(...)`; handler en
   `app/api/auth/[...path]/route.ts`.
4. Prisma: `authUserId String? @unique` + `passwordHash String?` — migración
   SQL a mano + `prisma migrate deploy` (nunca `migrate dev` contra la base
   real).
5. `getSessionUser()` dual: primero `auth.getSession()` → `User` por
   `authUserId` (vínculo por email / JIT); si no, cookie `caja_session` vieja
   (transición). Las ~99 llamadas no cambian de firma.
6. Formularios de login/registro **se conservan** (UI propia en español):
   las server actions pasan a llamar `auth.signIn.email` /
   `auth.signUp.email`. `/auth/salir` cierra ambas sesiones. Cambio de
   contraseña delega en Neon Auth.
7. `middleware.ts`: check optimista de cookie de Neon Auth **o**
   `caja_session`; bypass de `/api/mobile` intacto.

## Fase 2 — API móvil (dual, junto con Fase 1)

`requireMobileUser`: si el bearer tiene forma de JWT → verificar con `jose`
(JWKS remoto, EdDSA, iss/aud = origen) → `sub` = `authUserId` → `User`; si
no → rama opaca vieja (tabla `Session`), que sigue válida para APKs viejos.

## Fase 3a — Android, modo API (HECHA)

Implementado en la app:

1. `NeonAuthClient` (`data/remote/neonauth/`): `sign-in/email`,
   `sign-up/email`, `sign-out` y `GET /token` (JWT corto). La sesión es la
   **cookie firmada** del Set-Cookie, guardada tal cual (el cliente no puede
   reconstruir la firma); errores de Better Auth traducidos al español.
2. `gradle.properties: caja.neonAuthUrl` → `BuildConfig.NEON_AUTH_URL`
   (URL pública, compilable sin riesgo). Vacía = solo login legacy.
3. `AuthManager`: cookie de Neon Auth en DataStore + `updateToken()`.
4. `NeonTokenAuthenticator` (OkHttp): ante un 401, renueva el JWT con la
   cookie y reintenta una vez; si el refresh falla, el 401 sigue su curso y
   se declara `SessionExpired`. `AuthInterceptor` respeta un Authorization
   explícito (lo usa `/me` durante el login).
5. `ApiSyncSource.login` dual: Neon Auth titular → `/me` resuelve/vincula el
   usuario interno con el JWT; si Neon no conoce las credenciales, login
   legacy + **migración silenciosa** (sign-up con la misma contraseña).
   `register` va directo a Neon Auth. Tests con dos MockWebServer.

Nota: el modo API nuevo requiere el servidor web ya desplegado con las
Fases 1–2 (el `/me` con JWT y el vínculo viven allí).

## Fase 3b — Android, modo Neon directo (PENDIENTE)

El login scrypt (`NodeScrypt.kt`) sigue activo y FUNCIONA para cuentas
migradas (la migración silenciosa conserva la contraseña, el hash viejo
sigue siendo válido). No funciona para cuentas nuevas creadas solo en Neon
Auth (sin `passwordHash`). El reemplazo: autenticar contra Neon Auth y
mapear `sub → User.authUserId` por SQL; entonces mueren `NodeScrypt.kt`,
las filas `Session` del modo directo y Bouncy Castle.

El modo invitado no se toca en ninguna fase.

## Fase 4 — Limpieza (cuando no queden clientes viejos)

- Web: `password.ts`, `auth-core.ts`, rama opaca de `mobile-auth.ts`,
  rate-limit propio, modelo `Session`, columna `passwordHash`.
- Android: `NodeScrypt.kt`, endpoints `auth/*` de `SyncApi`.
- Docs: `neon-directo.md`, `mobile-api.md`, `CLAUDE.md`.

## Riesgos asumidos

- Usuarios existentes deben crear contraseña nueva (mismo email → sus datos
  se vinculan solos).
- APKs viejos rompen al reloguear salvo mantener la rama dual de Fase 2.
- El login depende del servicio Neon Auth (mismo proveedor que la base; no
  se añade ningún tercero nuevo).
- Los endpoints REST exactos para clientes nativos (Fase 3) se verifican en
  la documentación al implementarla.
