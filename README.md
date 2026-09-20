# Changebox

App Android de gestión de dinero y contabilidad personal — Kotlin + Jetpack
Compose + Material 3. Cuentas y grupos, movimientos (ingresos, gastos y
transferencias), arqueos de efectivo por denominaciones, deudas, planes de
pago, monedas y tasas de cambio.

Es el cliente móvil del proyecto web de Changebox (`F:\Projects\fantastic-eureka`,
Next.js + Prisma sobre Neon): funciona **offline-first** contra una base Room
local y sincroniza contra el servidor cuando hay red.

**La cuenta es opcional.** La app arranca directo en modo invitado: todas las
funciones operan contra la base local, con un catálogo por defecto sembrado
(las mismas monedas y categorías que el servidor da a un usuario nuevo). Solo
si se quiere respaldo y sincronización se conecta una cuenta desde **Más →
Cuenta y sincronización** — ahí viven las variables de conexión (URL del
servidor o cadena de Neon), el interruptor de sincronización periódica, el
"Sincronizar ahora" y el cierre de sesión. Al conectar, lo registrado como
invitado se sube a la cuenta (`GuestMigration` reescribe el outbox mapeando el
catálogo sembrado al del servidor) y los datos personales se rellenan solos.

## Requisitos

- Android Studio (última versión estable)
- Un dispositivo Android con depuración USB, o un emulador (API 26+)
- Solo para sincronizar: una instancia del proyecto web accesible (o la cadena
  de conexión de Neon, ver [Modos de backend](#modos-de-backend))

## Cómo arrancar

1. Abre Android Studio → **Open** → selecciona esta carpeta.
2. Espera el primer *Gradle sync* (descarga dependencias, tarda unos minutos
   solo la primera vez).
3. Conecta tu teléfono o arranca un emulador y pulsa **Run ▶**. La app entra
   directo, sin pedir cuenta.
4. Para sincronizar: **Más → Cuenta y sincronización**, guarda la conexión si
   el APK no trae servidor compilado (ver abajo) y entra con tus credenciales.

### El servidor que trae el APK

`changebox.serverUrl` en `gradle.properties` es la URL que viaja compilada en el
APK (llega al código como `BuildConfig.DEFAULT_SERVER_URL`):

| `changebox.serverUrl` | Formulario de acceso (Más → Cuenta y sincronización) |
|---|---|
| con valor | solo correo y contraseña — instalar y entrar, sin configurar |
| vacía | pide además el origen de datos y su URL |

Déjala **vacía en desarrollo** (así apuntas al Next.js de tu LAN desde el
propio formulario) y pásala al compilar la APK que vas a repartir:

```powershell
./gradlew assembleRelease "-Pchangebox.serverUrl=https://changebox.tudominio.com"
```

Quien instale esa APK solo pone correo y contraseña. El servidor sigue siendo
cambiable desde **Opciones avanzadas** del login o desde la sección
**Conexión** del menú Cuenta y sincronización, y ese cambio se guarda como
*override* en el DataStore app-privado (`data/auth/AuthManager.kt`): si más
adelante repartes una APK con otra URL, el override se limpia solo al coincidir
con la nueva compilada, así que un dispositivo viejo no se queda clavado en la
anterior.

La URL del servidor es lo **único** que se compila. La cadena de Neon nunca: da
acceso a toda la base y solo vive en el DataStore de quien la escribe.

Desde la terminal, `./gradlew` necesita `JAVA_HOME`. En esta máquina:

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

## Stack

| Qué | Versión |
|---|---|
| Kotlin | 2.4.0 |
| AGP | 8.13.2 |
| Gradle | 9.4.1 |
| Compose BOM | 2025.09.01 |
| Room | 2.8.4 |
| Retrofit / OkHttp | 3.0.0 / 4.12.0 |
| Min SDK / Target SDK | 26 / 36 |
| JVM target | 17 |

> Nota: las versiones androidx más recientes (core-ktx 1.18+, lifecycle 2.10+,
> BOM 2025.10+) requieren AGP 9.1 / compileSdk 37. Cuando Android Studio te
> proponga esa migración, actualiza AGP, compileSdk y librerías en conjunto.

Las versiones se declaran en `gradle/libs.versions.toml` (version catalog);
no las escribas a mano en los `build.gradle.kts`.

## Arquitectura

```
app/src/main/java/com/lolo/changebox/
├── ChangeboxApplication.kt      # Crea el AppContainer y agenda el sync periódico
├── MainActivity.kt              # Monta tema + navegación
├── data/
│   ├── auth/                    # AuthManager (DataStore), AuthRepository, NodeScrypt
│   ├── local/                   # ChangeboxDatabase (Room) + dao/ + entity/
│   ├── remote/                  # SyncSource, ApiSyncSource, RoutingSyncSource, SyncApi, Dtos
│   │   └── neon/                # NeonSyncSource, NeonSql, NeonHttpClient, NeonConnection
│   ├── repo/                    # AccountsRepository, LedgerRepository (escriben al outbox)
│   └── sync/                    # SyncEngine, OutboxWriter, SyncWorker, Mappers
├── di/                          # AppContainer (DI manual), changeboxViewModel
├── domain/                      # Money, BalancesCore, RateResolve, Counting, MetricsCore, Dates, Format
└── ui/
    ├── navigation/              # Routes.kt (rutas tipadas), AppNavHost.kt
    ├── theme/                   # Colores, tipografía, iconos de cuenta
    ├── components/              # Piezas compartidas: formularios, gráficas
    ├── auth/  dashboard/  accounts/  movements/  registertx/  more/
```

**Room es la única fuente de verdad de la UI.** Las pantallas nunca hablan con
la red: leen `Flow`s de los DAOs y escriben a través de los repositorios, que
aplican el cambio en local y encolan una operación en el *outbox*. El
`SyncEngine` drena ese outbox contra el servidor (push, FIFO, ≤200 ops) y a
continuación hace siempre un pull incremental por cursor. Un `SyncWorker`
periódico (cada 6 h), cada escritura y el pull-to-refresh comparten la misma
instancia, serializada con un mutex. Sin sesión el motor no hace nada: las
escrituras del modo invitado quedan en el outbox hasta que se conecte una
cuenta, y el worker periódico solo se agenda con sesión iniciada y la
sincronización periódica activada (interruptor en Cuenta y sincronización).

```
UI (Compose) ──lee── DAO (Flow) ──┐
                                  ├── CajaDatabase (Room)
UI ──escribe── Repository ── Outbox ──┘
                                  │
                            SyncEngine ──> SyncSource ──> servidor
```

Las reglas del dinero (conversión entre divisas, resolución de tasas, guardas
de saldo, generación de cuotas, idempotencia por `opId`) viven en el servidor,
no aquí. `domain/` solo contiene el cálculo que la UI necesita para presentar
(saldos derivados, formato, métricas), no una segunda fuente de verdad.

**DI manual** con `AppContainer`, creado una vez en `CajaApplication.onCreate`
y todo perezoso. Sin Hilt ni Koin a propósito: ~16 ViewModels no justifican el
framework.

**Navegación tipada** (navigation-compose + kotlinx-serialization): las rutas
son objetos y data classes `@Serializable` en `ui/navigation/Routes.kt`, espejo
de las rutas de la web.

> Estado: dashboard, cuentas, movimientos, registro de operaciones y auth están
> implementados. Arqueos, deudas, planes, tasas, monedas, categorías y perfil
> tienen ruta y aún resuelven a `PlaceholderScreen` (ver `AppNavHost.kt`).

## Modos de backend

`SyncEngine` no sabe qué hay detrás: habla contra la interfaz `SyncSource`, y
`RoutingSyncSource` elige la implementación en cada llamada según la
preferencia del usuario, así que cambiar de modo surte efecto al instante.

| Modo | Implementación | Origen | Lee | Escribe |
|---|---|---|---|---|
| **Servidor** | `ApiSyncSource` (Retrofit) | API móvil de Caja, `/api/mobile/v1` | sí | sí (todo el protocolo) |
| **Neon directo** | `NeonSyncSource` (OkHttp) | la base Neon, por SQL sobre HTTP | sí | sí (las ops que la app emite hoy) |

El modo Neon directo permite usar la app — registro, lectura y sincronización
de escrituras — sin que el proyecto web esté desplegado ni encendido. Su push
es un **espejo deliberado** de las reglas del servidor para las operaciones
que la app emite (cuentas, grupos, categorías y movimientos): si cambian los
servicios de la web, hay que actualizarlo (`data/remote/neon/NeonPush.kt`).
La cadena de conexión tiene implicaciones de seguridad que conviene entender
antes de compartirla: **léete [docs/neon-directo.md](docs/neon-directo.md)**
antes de usarlo.

## Tests

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
./gradlew test
```

Todo corre en la JVM, sin dispositivo: los tests de DAO usan Robolectric y los
de red un `MockWebServer`. Cubren el núcleo de dominio (dinero, saldos, tasas,
arqueos, métricas, fechas, formato), el ciclo del `SyncEngine` (cursor,
lápidas, resync, rechazos), la siembra del modo invitado y la migración de su
outbox al conectar una cuenta, el mapeo del contrato de Neon y el login con un
hash **real** de `node:crypto`.

El SQL de `NeonSql.kt` no lo cubre ningún test (no hay Postgres en la JVM); se
valida aparte contra el esquema del proyecto web — el procedimiento está en
[docs/neon-directo.md](docs/neon-directo.md#verificación).

## Equivalencias si vienes de JS/React

| Android/Kotlin | React/JS |
|---|---|
| `@Composable fun` | Componente funcional |
| `remember { mutableStateOf() }` | `useState` |
| `LaunchedEffect` | `useEffect` |
| `StateFlow` + `collectAsStateWithLifecycle` | Store (Zustand/Redux) + selector |
| `ViewModel` | Custom hook con estado que sobrevive re-renders |
| Corrutinas / `suspend` | `async/await` |
| Room + DAOs (`Flow`) | Prisma + queries reactivas |
| Retrofit + kotlinx-serialization | `fetch` + zod |
| Gradle + version catalog | npm + package.json |
