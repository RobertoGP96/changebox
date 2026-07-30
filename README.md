# Native Messenger

App de mensajería nativa Android — Kotlin + Jetpack Compose + Material 3.

## Requisitos

- Android Studio (última versión estable)
- Un dispositivo Android con depuración USB, o un emulador (API 26+)

## Cómo arrancar

1. Abre Android Studio → **Open** → selecciona esta carpeta.
2. Espera el primer *Gradle sync* (descarga dependencias, tarda unos minutos solo la primera vez).
3. Conecta tu teléfono o arranca un emulador y pulsa **Run ▶**.

## Stack

| Qué | Versión |
|---|---|
| Kotlin | 2.4.0 |
| AGP | 8.13.2 |
| Gradle | 8.14.5 |
| Compose BOM | 2025.09.01 |
| Min SDK / Target SDK | 26 / 36 |

> Nota: las versiones androidx más recientes (core-ktx 1.18+, lifecycle 2.10+,
> BOM 2025.10+) requieren AGP 9.1 / compileSdk 37. Cuando Android Studio te
> proponga esa migración, actualiza AGP, compileSdk y librerías en conjunto.

## Arquitectura (MVVM)

```
app/src/main/java/com/lolo/nativemessenger/
├── MainActivity.kt              # Punto de entrada, monta el tema y la navegación
├── data/
│   ├── model/Models.kt          # Conversation, Message
│   └── ChatRepository.kt        # Interfaz + implementación en memoria (mock)
├── di/ServiceLocator.kt         # DI manual (migrar a Hilt cuando crezca)
└── ui/
    ├── theme/                   # Colores, tipografía, tema Material 3
    ├── navigation/AppNavHost.kt # Rutas y navegación
    ├── conversations/           # Pantalla lista de chats + ViewModel
    └── chat/                    # Pantalla de conversación + ViewModel
```

**Flujo de datos:** `Repository (Flow) → ViewModel (StateFlow) → UI (collectAsStateWithLifecycle)`.
La UI nunca toca los datos directamente; para conectar un backend real solo
implementas `ChatRepository` de nuevo y no tocas ni ViewModels ni pantallas.

## Equivalencias si vienes de JS/React

| Android/Kotlin | React/JS |
|---|---|
| `@Composable fun` | Componente funcional |
| `remember { mutableStateOf() }` | `useState` |
| `LaunchedEffect` | `useEffect` |
| `StateFlow` + `collectAsStateWithLifecycle` | Store (Zustand/Redux) + selector |
| `ViewModel` | Custom hook con estado que sobrevive re-renders |
| Corrutinas / `suspend` | `async/await` |
| Gradle + version catalog | npm + package.json |

## Tests

```
./gradlew test          # unit tests (JVM, rápidos)
```

## Próximos pasos sugeridos

1. Backend real: implementa `ChatRepository` con Supabase Realtime o Firebase.
2. Persistencia local: Room (SQLite) para funcionar offline.
3. DI: migra `ServiceLocator` a Hilt cuando tengas +3 repositorios.
4. Push: Firebase Cloud Messaging para notificaciones.
5. CI: GitHub Actions con `./gradlew build` en cada push.
