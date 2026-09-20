# Changebox — réplica offline de fantastic-eureka

App Android nativa (Kotlin + Jetpack Compose, paquete `com.lolo.changebox`) que
replica la web de Changebox (`D:\Projects\fantastic-eureka`, Next.js) en
modo **100% offline**: sin inicio de sesión, sin sincronización, sin permiso
de INTERNET. Room es la única fuente de verdad. El repo y la carpeta son
`changebox` (antes `native-messenger`, herencia histórica ya corregida).

La web es la REFERENCIA FUNCIONAL: cualquier feature nueva se porta desde
allí (mismos textos en español, mismas reglas de negocio, mismos mensajes de
error). UI/comentarios en español.

La paridad NO está completa y la app aún no habla con la base de datos de la
web. El inventario de brechas y el plan por fases están en
`docs/plan-paridad-y-sincronizacion.md` — consúltalo antes de planificar
trabajo nuevo. Los `docs/neon-*.md` describen un prototipo con
sincronización que NO existe en este código: son histórico, no la
arquitectura actual.

## Comandos

- Compilar: `.\gradlew.bat :app:assembleDebug` — requiere
  `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`.
  OJO: el build completo tarda ~40 min en esta máquina; los incrementales
  bastante menos. APK en `app/build/outputs/apk/debug/`.
- Tests: `.\gradlew.bat :app:testDebugUnitTest` (dominio puro en JVM).
- El catálogo de versiones vive en `gradle/libs.versions.toml`.

## Arquitectura

- **Dinero**: SIEMPRE enteros en unidades menores (`Long`); tasas escaladas
  ×10 000 (`RATE_SCALE`). Aritmética en `domain/Money.kt` (BigInteger para
  conversiones), port 1:1 de la web con tests espejo. PROHIBIDO Float/Double
  para montos. Formateo "4 750 CUP" en `domain/Format.kt` (agrupado manual,
  nunca NumberFormat).
- **Dominio puro** (`domain/`): Money, Format, BalancesCore, RateResolve,
  Counting (sugeridor con backtracking para el billete de 3 CUP), Dates
  (recurrencia con clamp de fin de mes), MetricsCore, Domain (enums + labels
  ES). Ports 1:1 de `src/lib/*.ts` de la web, con tests en `app/src/test`.
- **Datos** (`data/local/`): esquema Room v2 espejo del prisma/schema.prisma
  SIN userId ni tablas de sync. Fechas en epoch millis. Los VENCIMIENTOS
  (`dueAt`, `endAt`) van a las 12:00 locales (`atNoonMillis`, como la web),
  pero el `occurredAt` de un movimiento lleva la fecha elegida + la HORA
  ACTUAL (`atCurrentTimeMillis`): así dos movimientos del mismo día
  conservan el orden en que se registraron, que es el que usan el historial
  (`ORDER BY occurredAt, createdAt`) y el stock por denominación. Los
  borrados en cascada de la web se replican con FKs: borrar CUENTA se lleva movimientos de AMBOS lados + arqueos +
  abonos (CASCADE); borrar DEUDA se lleva abonos y planes+cuotas; las cuotas
  saldadas conservan estado si cae su movimiento (SET_NULL). Denominaciones y
  categorías usadas NO se borran (RESTRICT + chequeo con mensaje amigable).
- **Seed** (`data/local/Seed.kt`): primera ejecución = port de
  user-defaults.ts (CUP base 0 dec con billete/moneda de 3, USD, EUR, MLC sin
  denominaciones, categorías base). Idempotente, corre en ChangeboxApplication.
- **Repos** (`data/repo/`): ports 1:1 de `src/lib/services/*-service.ts` con
  el MISMO contrato: fallos de negocio → `ActionResult.Failure` con el texto
  exacto de la web; validaciones de saldo/pendiente releídas DENTRO de
  `db.withTransaction` con `ActionError` (anti doble-envío). Saldos y stock
  de denominaciones SIEMPRE derivados, nunca almacenados
  (`AccountRepository`: balances por GROUP BY + balances-core; stock =
  último arqueo ± desgloses posteriores con `movementLineSign`).
- **Multi-moneda en registrar**: el movimiento se guarda en la moneda de la
  cuenta; el monto original queda en counterAmountMinor/counterCurrencyId y
  la tasa implícita en rateScaled (`impliedRateScaled`). Conversión inversa
  por división BigInteger (`convertMinorInverse`), tasa prellenada desde los
  pares vía `resolveRateScaled` (directo → inverso → compuesto vía base) y
  botón ⇄ que invierte la tasa escrita.
- **UI** (`ui/`): Compose Material 3, tema propio en `ui/theme` (paleta
  petróleo/esmeralda + dorado portada de globals.css, SIN dynamic color;
  fuente Outfit variable local). Navegación con bottom bar (Inicio, Cuentas,
  [+] central, Deudas, Más) espejo del bottom-nav web. Componentes
  compartidos en `ui/common` (ScreenHeader con gradiente, ChangeboxCard, badges,
  DenominationCounter, gráficos Canvas sin librerías). DI manual:
  `di/AppContainer` + `appViewModel {}`.
- **Export CSV**: `LedgerRepository.exportCsv` (mismas columnas y BOM que
  /api/export de la web) → archivo en cache/exports → FileProvider + hoja de
  compartir (manifest `${applicationId}.fileprovider`).
- El "perfil" de la web se convirtió en **Ajustes** (nombre local en
  DataStore); no hay correo/contraseña porque no hay cuentas.

## Gotchas

- Cambiar el esquema Room exige subir `version` en ChangeboxDatabase **y
  escribir la `Migration`** (v1→v2 ya existe: `Currency.kind`). El
  `fallbackToDestructiveMigration(dropAllTables = true)` sigue puesto como
  red de seguridad para saltos sin migración, no como sustituto: si lo dejas
  actuar, el usuario pierde sus datos.
- Toda operación de escritura de un repo va envuelta en
  `guarded("No se pudo …") { … }` (`data/Results.kt`), con el MISMO texto
  genérico que el `catch` de la server action equivalente: un fallo
  inesperado se convierte en `Failure` en vez de tumbar la app. Dentro de esa
  lambda los `return` son `return@guarded`.
- `Currency.kind` = CASH | DIGITAL: una moneda digital (MLC) no lleva
  denominaciones ni admite cuentas CASH/CASH_BOX.
- Los `@Query` con filtros opcionales usan el patrón
  `(:param IS NULL OR col = :param)` — mantenerlo al añadir filtros.
- VENCIDA es estado DERIVADO (cuota PENDING con dueAt pasado): no hay
  cron ni notificaciones push; la campana de Inicio lee de
  `upcomingInstallmentsFlow` (≤7 días, máx 8 en el dropdown).
- Al saldar/omitir una cuota, `advancePlan` genera la siguiente (upsert por
  planId+dueAt) o desactiva el plan (ONCE, endAt alcanzado o deuda saldada).
- Un abono que iguala el pendiente marca la deuda PAID, omite sus cuotas
  pendientes y desactiva sus planes — igual que la web.

