# Changebox

App Android de gestión de dinero y contabilidad personal — Kotlin + Jetpack
Compose + Material 3. Cuentas y grupos, movimientos (ingresos, gastos y
transferencias), arqueos de efectivo por denominaciones, deudas, planes de
pago, monedas y tasas de cambio.

Es una réplica **100 % offline** del proyecto web de Changebox
(`D:\Projects\fantastic-eureka`, Next.js + Prisma sobre Neon): la app no
tiene inicio de sesión, no sincroniza y **no declara el permiso de
INTERNET**. Room es la única fuente de verdad y todo vive en el dispositivo.

La web es la **referencia funcional**: cada pantalla es un port de la suya,
con los mismos textos en español, las mismas reglas de negocio y los mismos
mensajes de error.

> Conectar la app con la base de datos de la web (cuenta, sincronización
> bidireccional y las funciones que aún faltan) es un trabajo planificado,
> no implementado: ver
> [docs/plan-paridad-y-sincronizacion.md](docs/plan-paridad-y-sincronizacion.md).

## Requisitos

- Android Studio (última versión estable)
- Un dispositivo Android con depuración USB, o un emulador (API 26+)

## Cómo arrancar

1. Abre Android Studio → **Open** → selecciona esta carpeta.
2. Espera el primer *Gradle sync* (descarga dependencias, tarda unos minutos
   solo la primera vez).
3. Conecta tu teléfono o arranca un emulador y pulsa **Run ▶**. La app entra
   directo: no pide cuenta ni configuración.

En la primera ejecución se siembra el catálogo por defecto (las mismas
monedas, denominaciones y categorías que la web da a un usuario nuevo; ver
`data/local/Seed.kt`).

Desde la terminal, `./gradlew` necesita `JAVA_HOME`. En esta máquina:

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. El build completo tarda
~40 min en esta máquina; los incrementales, bastante menos.

## Stack

| Qué | Versión |
|---|---|
| Kotlin | 2.4.0 |
| AGP | 8.13.2 |
| Gradle | 9.4.1 |
| Compose BOM | 2025.09.01 |
| Room | 2.8.4 |
| Min SDK / Target SDK | 26 / 36 |
| JVM target | 17 |

Sin librerías de red, de gráficos ni de inyección de dependencias: los
gráficos se dibujan con Canvas de Compose y el grafo de objetos es un
contenedor manual.

> Nota: las versiones androidx más recientes (core-ktx 1.18+, lifecycle 2.10+,
> BOM 2025.10+) requieren AGP 9.1 / compileSdk 37. Cuando Android Studio te
> proponga esa migración, actualiza AGP, compileSdk y librerías en conjunto.

Las versiones se declaran en `gradle/libs.versions.toml` (version catalog);
no las escribas a mano en los `build.gradle.kts`.

## Arquitectura

```
app/src/main/java/com/lolo/changebox/
├── ChangeboxApplication.kt      # Crea el AppContainer y siembra el catálogo
├── MainActivity.kt              # Monta tema + navegación
├── data/
│   ├── local/                   # ChangeboxDatabase (Room) + dao/ + entity/
│   ├── prefs/                   # UserPrefs (DataStore): nombre local
│   ├── repo/                    # Un repositorio por área (reglas de negocio)
│   ├── Results.kt               # ActionResult<T> = éxito | fallo con mensaje
│   └── Time.kt                  # Fechas ↔ epoch millis
├── di/                          # AppContainer (DI manual), appViewModel
├── domain/                      # Money, Format, BalancesCore, RateResolve,
│                                # Counting, Dates, MetricsCore, Domain
└── ui/
    ├── ChangeboxApp.kt          # Rutas + barra inferior
    ├── theme/                   # Colores, tipografía, iconos de cuenta
    ├── common/                  # Piezas compartidas: tarjetas, gráficos, listas
    └── home/ accounts/ movements/ register/ counting/ debts/ catalog/
        rates/ more/
```

**Room es la única fuente de verdad.** Las pantallas leen `Flow`s de los DAOs
y escriben a través de los repositorios; no hay red ni caché intermedia.

Los repositorios son ports 1:1 de las *server actions* de la web
(`src/app/actions/*.ts`): devuelven `ActionResult.Failure` con el **texto
exacto** de la web ante un fallo de negocio, y releen las validaciones de
saldo y pendiente **dentro** de `db.withTransaction` (patrón anti
doble-envío).

**Dinero**: siempre enteros en unidades menores (`Long`); tasas escaladas
×10 000. La aritmética vive en `domain/Money.kt` (BigInteger en las
conversiones), portada 1:1 de `src/lib/money.ts`. Prohibido usar
`Float`/`Double` para montos.

**Saldos derivados**, nunca almacenados: se calculan con `GROUP BY` +
`domain/BalancesCore.kt`. El stock por denominación de una caja es el último
arqueo ± los desgloses posteriores.

**DI manual** con `AppContainer`, creado una vez en
`ChangeboxApplication.onCreate`. Sin Hilt ni Koin a propósito: el grafo es
plano.

## Tests

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest
```

Corren en la JVM, sin dispositivo. Cubren el núcleo de dominio con tests
espejo de los de la web: dinero, formato, saldos, tasas, conteo de
denominaciones, métricas y fechas de recurrencia.

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
| Gradle + version catalog | npm + package.json |
