# Plan: paridad con la web y soporte de la base de datos

Fecha: 2026-09-20. Última revisión: 2026-09-20 (Fase 0 completada).

Objetivo: que la APK (repo `changebox`, paquete `com.lolo.changebox`) haga
todo lo que hace la web (`D:\Projects\fantastic-eureka`, Next.js 15 + Prisma
sobre Neon) y que un usuario que ya usa la web pueda entrar en la APK, ver
sus datos y trabajar offline con sincronización bidireccional.

Estado por fases:

| Fase | Estado |
|---|---|
| 0 — Sanear el repo | **Hecha** |
| 1 — Paridad de esquema y dominio | **Hecha** |
| 2 — Paridad funcional | Pendiente |
| 3 — Servidor: servicios + API móvil | Pendiente |
| 4 — Cliente: red y sincronización | Pendiente |
| 5 — Funciones que necesitan red | Pendiente |
| 6 — QA y release | Pendiente |

> Nota: la web NO está en `F:\Projects\fantastic-eureka` (esa unidad no
> existe en esta máquina). La copia viva es `D:\Projects\fantastic-eureka`
> (= `origin/main` de GitHub).

---

## 0. Diagnóstico (lo que hay hoy)

### 0.1 APK

- **Renombrado y commit: resueltos en la Fase 0.** El diagnóstico original
  era que el árbol estaba a medio renombrar de `com.lolo.caja` a
  `com.lolo.changebox` (11 referencias al paquete viejo y un cast a
  `CajaApplication`), que nada estaba commiteado y que las docs describían
  un prototipo inexistente. Ver el detalle de lo aplicado en la Fase 0.
- **Sin red por diseño**: sin permiso INTERNET, sin auth, sin `userId` en
  Room, sin metadatos de sincronización. Room v1 espejo del Prisma menos
  `Currency.kind`, `User`, `Session`, `AccountShare`.
- **Lo bueno**: dominio y repos ya son ports 1:1 con los mismos textos de
  error (verificado por grep: ~60 mensajes idénticos), tests JVM del
  dominio, export CSV byte a byte igual al de la web.

### 0.2 Web

- **No existe API móvil** (`src/app/api/` solo tiene `export/route.ts`), ni
  `src/lib/sync/`, ni `src/lib/services/`, ni tablas `SyncOperation` /
  `SyncTombstone`, ni Neon Auth (no está `@neondatabase/auth` en
  package.json). Ninguna rama local ni remota las tiene.
- **Auth propia**: scrypt (`src/lib/password.ts`) + tabla `Session` con
  token opaco (SHA-256) en cookie `caja_session`, 30 días.
- **Lógica de negocio en server actions** (`src/app/actions/*.ts`, ~2 900
  líneas) acopladas a `getSessionUser()` (cookies) y `revalidatePath`.
  Para reutilizarla desde una API hay que extraerla.
- **Migración fantasma**: el CLAUDE.md de la web dice que la BD Neon tiene
  aplicada `20260715233430_mobile_sync_foundation` (añade `updatedAt` a
  varias tablas) que **no está en el repo**. No pude verificarlo desde esta
  sesión (lectura de la BD de producción denegada). Es el primer punto a
  comprobar (ver §6).
- Montos son `Int` de 32 bits en Prisma (`PRISMA_INT_MAX`); la APK usa
  `Long` pero ya valida `SERVER_INT_MAX` con el mismo valor.

### 0.3 Brechas funcionales (resumen; detalle en §2)

| Módulo | Estado APK | Falta |
|---|---|---|
| Movimientos | Parcial | **Editar y eliminar movimiento**; orden cronológico ascendente; hora real (hoy todo a las 12:00) |
| Mensualidades | Ausente | Pantalla propia con filtros, compromiso mensual, saldar desde la lista, finalizadas; `plans-core` |
| Deudas | Parcial | Filtro "Todas" por defecto, totales "Me deben/Debo", chip abiertas, avatar y barra de progreso |
| Inicio | Parcial | Dashboard personalizable (`dashboardPrefs`), panel bento, 4 gadgets, `IncomeCard`, chip "Neto mes" |
| Cuentas | Parcial | Vista Archivadas, chips por divisa en grupos, gráfico de actividad, compartir cuenta (necesita red) |
| Registrar | Parcial | `drivesAmount` (contar primero rellena el monto), conservar desglose al cambiar tipo, volver a la cuenta |
| Monedas | Parcial | `Currency.kind` CASH/DIGITAL: selector al crear, badge, editor de clasificación y sus 3 reglas |
| Calculadora | Parcial | "Compartir conteo" (`buildCountShareText`) |
| Arqueo | Parcial | Tras guardar volver al detalle de la cuenta |
| Perfil | N/A | Correo y contraseña (llegan con la cuenta, §4) |
| Cuentas/Categorías/Tasas/Grupos/Conteo/Export | Completo | Solo copys menores |

---

## 1. Decisiones de arquitectura

### 1.1 Cómo se conecta la APK a los datos: API móvil en la web (recomendado)

Se descartan las otras dos opciones que ya exploraron los docs viejos:

| Opción | Pros | Contras | Veredicto |
|---|---|---|---|
| **A. API móvil `/api/mobile/v1` en Next.js** + outbox en la app | Una sola implementación de reglas (la web valida saldos, pendientes, cuotas); sin repartir credenciales de BD; Vercel ya la sirve; sesión = tabla `Session` existente | Hay que extraer servicios de las actions y escribir la API | **Elegida** |
| B. Neon directo por `/sql` con cadena de owner | Sin tocar la web | Regala toda la base a quien tenga la cadena; espejo de reglas en SQL que se desvía en silencio | No |
| C. Neon Auth + RLS + Data API | Sin servidor propio para lecturas | Neon Auth no está en la web; cambia el login de todos los usuarios; la app escribiría sin validación del servidor | Quizá más adelante (§7) |

### 1.2 Principios que se mantienen

- **Room sigue siendo la única fuente de verdad de la UI**. Las pantallas no
  hablan con la red. Escribir = repo aplica en local **y** encola una op en
  el outbox. Leer = Flows de Room.
- **La cuenta es opcional**: modo invitado 100 % offline como hoy. Conectar
  cuenta = respaldo + sincronización. Sesión caducada solo pausa el sync.
- **Un usuario por instalación**: Room no lleva `userId`. Cambiar de cuenta
  borra la BD local (con aviso) y hace bootstrap de la nueva.
- **IDs generados en el cliente** (UUID) y aceptados por el servidor
  (Prisma admite `id` explícito). Así el registro offline es definitivo.
- **Las reglas de negocio de la APK se conservan** como validación optimista
  local (feedback inmediato), pero **la decisión final es del servidor**: una
  op rechazada se revierte con el siguiente pull y queda visible en
  "Pendientes de sincronizar".

### 1.3 Protocolo de sincronización

Servidor (Prisma, aditivo, nada rompe la web):

- `updatedAt DateTime @updatedAt` en las 15 tablas de datos (si la
  migración fantasma ya lo puso en algunas, completar el resto) + tabla
  `SyncTombstone(userId, collection, rowId, deletedAt)` que se rellena en
  cada borrado (`deleteAccount`, `deleteTransaction`, `deleteDebt`,
  `deletePlan`, denominaciones, categorías, grupos, tasas no se borran).
- `SyncOperation(userId, opId @unique, type, status, error, createdAt)` para
  idempotencia: registrar la op **en la misma transacción** que la mutación.
- Endpoints (`src/app/api/mobile/v1/…`, Bearer token; el middleware ya
  excluye rutas por matcher, añadir `/api/mobile`):
  - `POST auth/login`, `POST auth/register`, `POST auth/logout`, `GET me`
    (reutilizan `password.ts`, `Session` y `bootstrapUserDefaults`).
  - `GET sync/pull?cursor=<iso>`: todas las colecciones con
    `updatedAt > cursor − 60 s` filtradas por `userId` + lápidas desde el
    cursor + `nextCursor = now()`. Sin cursor = bootstrap completo. Cursor
    de más de 60 días → `resync: true`.
  - `POST sync/push { ops: [{opId, type, payload}] }` (≤ 200, FIFO): cada op
    se aplica con el **servicio** correspondiente; respuesta por op:
    `applied | duplicate | rejected(error)`. Tras el push, el cliente hace
    pull.
- Tipos de op = 1:1 con las server actions que mutan (create/update/delete
  de cuenta, grupo, categoría, moneda, denominación, tasa, arqueo,
  movimiento, edición y borrado de movimiento, deuda, abono, plan, cuota
  saldar/omitir/desactivar, dashboardPrefs, perfil).

Cliente:

- Room v2: tabla `sync_outbox(opId, type, payloadJson, createdAt, status,
  error)`, tabla `sync_state(cursor, userId, lastPullAt)` y columna
  `updatedAt` en las entidades para el merge por *last-writer-wins* del
  catálogo. Añadir `Currency.kind` y `dashboardPrefs` (DataStore).
- `SyncEngine` (mutex): push del outbox → pull → upsert por id + aplicar
  lápidas, en **una** transacción Room. Disparadores: cada escritura con
  red, pull-to-refresh, `WorkManager` periódico (6 h, solo con sesión).
- Conflictos: catálogo = gana el `updatedAt` mayor; libro mayor = el
  servidor manda (`rejected` → revertir local con el pull y avisar).
- Migración invitado → cuenta: tras el bootstrap, mapear el catálogo
  sembrado al del servidor (monedas por `code`, categorías por
  `name+kind`, denominaciones por `currency+valueMinor+kind`), reescribir
  esos ids en Room y en el outbox, y subir el resto.

---

## 2. Fases

Cada fase deja la app usable y se puede commitear y repartir por separado.

### Fase 0 — Sanear el repo (bloqueante, ~1 día)

1. Terminar el renombrado a `com.lolo.changebox`: corregir las 11
   referencias a `com.lolo.changebox.*` y el cast de `MainActivity`; decidir el
   nombre visible (hoy "Changebox" en `strings.xml`, "Caja" en el código y
   `FantEurk` en la web).
2. Borrar `com.lolo.nativemessenger/**` y su test.
3. Compilar (`:app:assembleDebug`, ~40 min) y pasar `:app:testDebugUnitTest`.
4. **Commit inicial real** de la app (hoy todo es *untracked*).
5. Reescribir `README.md` y `CLAUDE.md` con el estado real (offline hoy,
   ruta `D:\` de la web) y mover `docs/neon-*.md` a un apartado "histórico".

### Fase 1 — Paridad de esquema y dominio — HECHA

Room va por v2 con **migración real** (`MIGRATION_1_2`), no por borrado
destructivo: el fallback queda solo como red de seguridad. Lo aplicado:

- `CurrencyEntity.kind` (CASH/DIGITAL) + `CurrencyKind` en `Domain.kt`;
  `Seed.kt`: MLC digital sin denominaciones; `createCurrency` recibe `kind`
  y no siembra denominaciones si es digital.
- Hora real en `occurredAt` (fecha elegida + hora actual) en registrar,
  arqueo, abono y cuota, como `register-form.tsx:307-319`.
- Desempate por `createdAt` en `TransactionDao` y en el export.
- Nuevos módulos de dominio puro con tests espejo: `PlansCore.kt`
  (`plans-core.ts`), `AccountActivity.kt` (`account-activity.ts`),
  `IncomeSeries.kt` (`income-series.ts`), `DashboardPrefs.kt`
  (`dashboard-prefs.ts`, serializado en DataStore; luego se sincroniza como
  `User.dashboardPrefs`), `CountShare.kt` (`buildCountShareText`).
- Red de seguridad genérica: envolver cada operación de repo en
  `runCatching` → `ActionResult.Failure("No se pudo …")` con el mismo texto
  que la action web (hoy una `SQLiteException` crashea).
- Copys: "Saldada" (cuota PAID), "Crear mensualidad"/"Mensualidad creada",
  "Finalizar mensualidad", "Activa/Finalizada".

### Fase 2 — Paridad funcional (~2 semanas)

En orden de valor para quien ya usa la web:

1. **Editar y eliminar movimientos**: `LedgerRepository.updateTransaction`
   y `deleteTransaction` (port de `transaction-actions.ts:400-683`, con sus
   5 reglas: sin ajustes, monto del otro lado obligatorio, tope
   pendiente+abono, `paidAt` sigue a `occurredAt`, reabrir deuda PAID;
   bloquear el ajuste de un arqueo), pantalla `movimientos/{id}/editar` y
   botones en el detalle.
2. **Mensualidades** como vista propia (`/mensualidades`,
   `/mensualidades/nueva`, `/mensualidades/{id}`): filtros
   Todas/Que pago/Que cobro, cabecera "Pago al mes / Cobro al mes", chips
   activas/vencidas, orden por urgencia, `SettleInstallment` embebido si
   vence en ≤ 7 días, sección Finalizadas; entrada en Más y enlaces desde
   Inicio y Deudas. `PlanDao` deja de filtrar `debtId IS NULL`.
3. **Deudas**: filtro "Todas" por defecto, totales por moneda Me deben/Debo,
   chip "N abiertas", avatar con iniciales, barra de progreso, enlace "Ver
   mensualidad".
4. **Registrar**: `drivesAmount` en `Denominations.kt` (contar primero
   rellena el monto; bloque antes del monto), conservar el desglose al
   cambiar de tipo, tras guardar ir al detalle de la cuenta; lo mismo tras
   un arqueo.
5. **Movimientos**: orden cronológico ascendente en lista y detalle de
   cuenta (`.reverse()` de la web).
6. **Monedas digitales**: selector al crear, badge "Digital", "sin
   efectivo", `setCurrencyKind` con sus dos reglas, filtro y aviso en nueva
   cuenta, regla "`X` es digital: usa una cuenta de tipo Banco o Digital".
7. **Cuentas**: vista Archivadas (N), chips por divisa en grupos
   (`totalsByCurrency` ya existe en `BalancesCore`), gráfico de actividad
   con períodos 7d/1m/3m/1a/Todo, `hasUsage` contando todo el libro y los
   arqueos.
8. **Inicio personalizable**: secciones ordenables/ocultables, panel de
   gadgets (`accountCard`, `currencyTotals`, `ratePair`, `incomeCard`),
   `IncomeCard` con tabs Día/Semana/Mes, chip "Neto mes", ocultar Por
   cobrar/Por pagar cuando valen 0, quitar "Total consolidado" (la web no lo
   tiene). El arrastre bento se hace con `LazyColumn` + reordenación por
   asa; la persistencia va a `DashboardPrefs` local.
9. **Calculadora**: botón Compartir con `buildCountShareText` (hoja de
   compartir nativa).

### Fase 3 — Servidor: servicios + fundación de sync + API móvil (~1,5-2 semanas, en la web)

1. Verificar el estado real de la BD (§6) y **traer al repo** la migración
   `mobile_sync_foundation` (o recrearla con `prisma migrate diff` desde la
   BD) para que `migrate deploy` no se rompa.
2. Extraer `src/lib/services/*.ts`: funciones `(db, userId, input) →
   Result` sin cookies ni `revalidatePath`. Las actions pasan a ser
   envoltorios (sesión + servicio + revalidate). Los tests existentes de
   `src/lib` siguen valiendo; añadir tests de servicio con la BD E2E
   (`caja_e2e`, rama `claude/suspicious-mendel`).
3. Migración aditiva: `updatedAt` donde falte, `SyncTombstone`,
   `SyncOperation`, índices por `(userId, updatedAt)`.
4. `src/lib/sync/{serialize,pull,push}.ts` y rutas
   `src/app/api/mobile/v1/{auth,me,sync}`. Auth Bearer contra `Session`
   (token opaco, renovación deslizante 30/15 días); `ALLOW_REGISTRATION`
   respetado; rate-limit sencillo en login.
5. Documentar el contrato en `docs/mobile-api.md` (ambos repos) y probarlo
   con Playwright `request`.

### Fase 4 — Cliente: red, cuenta y sincronización (~2 semanas)

1. Gradle: permiso INTERNET, OkHttp + Retrofit + kotlinx-serialization
   converter, WorkManager, `BuildConfig.DEFAULT_SERVER_URL` desde
   `gradle.properties` (vacío en dev → se pide en el login).
2. `data/auth`: `AuthManager` (DataStore cifrado con EncryptedSharedPrefs o
   `security-crypto`), `AuthState = Guest | SignedIn | SessionExpired`,
   interceptor Bearer, 401 → `SessionExpired` sin borrar datos.
3. Room v3: `sync_outbox`, `sync_state`, `updatedAt` en entidades; los
   repos escriben en local + outbox (`OutboxWriter`) en la misma
   transacción.
4. `SyncEngine` + `SyncWorker` + pull-to-refresh en Inicio, Cuentas,
   Movimientos, Deudas y Mensualidades; indicador de estado (última
   sincronización, N pendientes, N rechazadas).
5. Pantalla **Más → Cuenta y sincronización**: login/registro, URL del
   servidor (opciones avanzadas), interruptor de sync periódico,
   "Sincronizar ahora", cerrar sesión (borrar local con confirmación),
   lista de ops rechazadas con su mensaje.
6. `GuestMigration` al conectar por primera vez (mapeo del catálogo, ver
   §1.3). `dashboardPrefs` y el nombre pasan de DataStore a `User`.
7. Tests JVM con `MockWebServer`: ciclo push/pull, cursor, lápidas,
   resync, duplicados, rechazos, migración de invitado.

### Fase 5 — Funciones que necesitan red (~3-4 días, tras la 4)

- **Compartir cuenta**: `AccountShare` no se replica en Room; la app llama a
  `POST/DELETE /api/mobile/v1/accounts/{id}/share` y muestra el panel
  (enlace, "Vence en", copiar, compartir, nuevo código, revocar). La vista
  pública `/compartir/[token]` sigue siendo la web (enlace absoluto con la
  URL del servidor). Sin sesión, el panel explica que requiere cuenta.
- **Perfil**: correo y contraseña (`updateProfile`, `updatePassword` →
  invalida las otras sesiones) sustituyen a la pantalla Ajustes cuando hay
  sesión; sin sesión se queda como hoy.
- Texto "Caja · versión offline" pasa a describir el modo invitado.

### Fase 6 — QA y release (~1 semana)

- Prueba cruzada real: usuario existente de la web entra en la APK →
  bootstrap → registra offline en el móvil y en la web a la vez → sync →
  saldos iguales en ambos (script de comparación por CSV export).
- Pruebas de conflicto: mismo movimiento editado en ambos lados; borrar
  cuenta en web con movimientos pendientes en el móvil (lápida + ops
  rechazadas visibles).
- `assembleRelease` con `-Pcaja.serverUrl=https://…` (Vercel), ProGuard
  con reglas para kotlinx-serialization/Retrofit, subir `versionCode`.
- Dejar de usar `fallbackToDestructiveMigration` y probar la migración
  v1→v3 con datos de invitado reales.

---

## 3. Orden y dependencias

```
Fase 0 ──► Fase 1 ──► Fase 2 (paridad, sin red) ──┐
                                                   ├──► Fase 4 ──► Fase 5 ──► Fase 6
Fase 3 (web: servicios + API) ─────────────────────┘
```

Fase 3 se puede hacer en paralelo a la 2 (repos distintos). Fase 4 exige
las dos: los tipos de op del push son las mismas operaciones que los repos
Kotlin ya deben tener completas (editar/eliminar movimiento, etc.).

Estimación total: **7-9 semanas** de trabajo a tiempo completo; la mayor
incertidumbre está en la Fase 3 (extraer servicios sin romper la web) y en
la 4 (merge y conflictos).

---

## 4. Riesgos y cómo se acotan

| Riesgo | Mitigación |
|---|---|
| El árbol Kotlin no compila y el build tarda 40 min | Fase 0 primero; activar build incremental y `--offline` en Gradle; compilar solo `:app:compileDebugKotlin` para iterar |
| Refactor de actions rompe la web | Extraer servicio por servicio con las mismas firmas de `ActionResult`; E2E existentes como red; desplegar por PR |
| Migración fantasma en Neon | Verificar con `prisma migrate status` antes de escribir migraciones; todas aditivas |
| Doble validación (APK y servidor) se desvía | Tests espejo por mensaje de error (ya existe la práctica); el servidor manda y la APK muestra el rechazo |
| Cambiar de cuenta en un móvil con datos de invitado | Confirmación explícita + export CSV automático antes de borrar |
| Montos `Long` en Room vs `Int` en Prisma | Ya validado con `SERVER_INT_MAX`; el push rechaza lo que exceda |
| Room v1 con `fallbackToDestructiveMigration` | Solo hasta el primer release con usuarios; luego migraciones probadas con `app/schemas` |

---

## 5. Qué NO entra (por ahora)

- Sidebar/breadcrumb de escritorio y PWA: no aplican en nativo.
- Neon Auth / RLS / Data API: el login sigue con las credenciales actuales
  de la web para no obligar a nadie a crear contraseña nueva. Queda como
  evolución (§7).
- Notificaciones push de cuotas: siguen siendo derivadas al abrir la app.
- Multiusuario en un mismo dispositivo.

---

## 6. Verificaciones previas (a hacer por el dueño del proyecto)

1. **Estado de la BD Neon** (desde la web, no pude ejecutarlo aquí):

   ```bash
   cd D:/Projects/fantastic-eureka && npx prisma migrate status
   ```

   y

   ```bash
   cd D:/Projects/fantastic-eureka && npx prisma db pull --print | grep -nE "^model |updatedAt|Sync"
   ```

   Si aparece `mobile_sync_foundation` aplicada o columnas `updatedAt`,
   Fase 3.1 las adopta; si no, se crean desde cero.
2. **¿Existe todavía la copia de `F:\Projects\fantastic-eureka`?** Si en
   algún disco o máquina quedó una versión con `src/lib/sync/`,
   `src/lib/services/` y `api/mobile/v1` (el README y los docs de este repo
   los describen como hechos), recuperarla ahorra buena parte de la Fase 3.
   Lo mismo para el prototipo Android con `SyncEngine`/`NeonSyncSource`.
3. Confirmar el **nombre público** de la app (Caja / Changebox / FantEurk)
   antes de la Fase 0.5.

---

## 7. Evolución posterior (opcional)

- Neon Auth (+ RLS) como en `docs/neon-auth-migracion.md`, cuando se
  quiera quitar la tabla `Session` y la gestión propia de contraseñas. La
  API móvil de la Fase 3 acepta entonces JWT además del token opaco.
- Lecturas directas vía Neon Data API con RLS (`docs/neon-rls.sql`) para
  quitar carga a Vercel; las escrituras seguirían pasando por la API para
  conservar la validación del servidor.

