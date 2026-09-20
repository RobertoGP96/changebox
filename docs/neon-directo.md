# Modo Neon directo

La app puede hablar **directamente con la base Neon**, por SQL sobre HTTP,
sin que la API móvil (`/api/mobile/v1`) esté desplegada ni encendida:
registro de usuario, lectura y sincronización de escrituras — para las
operaciones que la app emite hoy.

El origen se elige en **Más → Cuenta y sincronización** (sección Conexión) o
en las opciones avanzadas del formulario de acceso:

| Modo | Origen | Lee | Escribe |
|---|---|---|---|
| **Servidor** | API móvil de Caja | sí | sí (todo el protocolo) |
| **Neon directo** | la base Neon, por SQL | sí | sí (las ops de la app: cuentas, grupos, categorías y movimientos) |

## Cómo se configura

1. En Neon → **Connection string**, copia la cadena (la del *pooler* sirve).
2. En la app: **Más → Cuenta y sincronización** → Conexión → **Neon directo**
   → pégala en *Cadena de conexión* → guarda y entra con tu correo y
   contraseña de siempre.

No hace falta tocar el código: la cadena **no se compila en el APK**, la
introduce quien usa la app y se guarda en el DataStore app-privado.

## Cómo funciona

```
SyncEngine ──> SyncSource ──┬── ApiSyncSource   (Retrofit → /api/mobile/v1)
                            └── NeonSyncSource  (OkHttp → https://<host>/sql)
```

`RoutingSyncSource` lee la preferencia en cada llamada, así que cambiar de modo
surte efecto al instante. Room sigue siendo la única fuente de verdad de la UI:
cambia de dónde se rellena, no cómo se consume.

El endpoint `/sql` es el mismo que usa el driver serverless de Neon para JS:
un POST por consulta, sin conexión TCP ni driver de Postgres en el teléfono.

**El JSON lo construye Postgres**, no Kotlin (ver `NeonSql.kt`): la respuesta
es byte a byte el mismo contrato que sirve la API, así que se decodifica con
los DTOs y `Mappers.kt` que ya existían. No hay una segunda capa de mapeo que
se pueda desviar del servidor en silencio.

`NEON_SYNC_QUERY` es un espejo de `src/lib/sync/{pull,serialize}.ts` del
proyecto web: mismas colecciones, mismo filtro por `userId`, mismo solape de
60 s, mismo cursor de `now()` y mismo resync forzado a los 60 días.

El login verifica la contraseña contra `User.passwordHash` replicando el
`scrypt` de `node:crypto` (`NodeScrypt.kt`) y crea una fila real en `Session`,
así que el token vale también si luego se vuelve al modo Servidor. El
registro replica `createUserWithDefaults`: usuario + catálogo por defecto
(el mismo `SeedCatalog` del modo invitado y de la web) + sesión, en **una
transacción** del endpoint de Neon.

## Cómo escribe (y qué implica)

El push (`NeonPush.kt`) es un espejo de `src/lib/sync/push.ts` y de los
núcleos de `src/lib/services` del proyecto web, limitado a las operaciones
que la app móvil emite hoy: `account.*`, `group.*`, `category.create`,
`tx.incomeExpense` y `tx.transfer`. Mantiene las piezas del protocolo:

- **idempotencia por `opId`** (tabla `SyncOperation`), con el registro dentro
  de la MISMA transacción que la mutación — sin la ventana de crash que
  documenta `push.ts`;
- **lápidas** (`SyncTombstone`) calculadas dentro de la transacción del
  borrado, para que otros dispositivos repliquen los deletes en su pull;
- **la misma aritmética** de `domain/Money.kt` que ya usaba la escritura
  optimista local (no hay una tercera implementación);
- `updatedAt` explícito en cada escritura (el `@updatedAt` de Prisma es de
  aplicación), para que el pull incremental de otros dispositivos vea los
  cambios.

**El coste, asumido a sabiendas**: es una segunda implementación de esas
reglas. Si cambias los servicios del proyecto web, este espejo debe cambiar
con ellos — revísalo en cada cambio de `services/` o `push.ts`.

Una operación que este modo **no** conoce (deudas, planes, arqueos, tasas…
cuando la app las emita) responde `skipped`: se queda en el outbox y se
sincroniza al cambiar al modo Servidor. Nunca se rechaza ni se pierde.

## Seguridad: léelo antes de compartir la cadena

La base **no tiene RLS**. El aislamiento por usuario lo hace la aplicación con
`WHERE "userId"` (tanto aquí como en el servidor). La cadena de conexión da
acceso de lectura y escritura a **toda la base**, no solo a los datos de quien
la pega.

Consecuencias prácticas:

- El modo Neon es razonable para **quien es dueño de la base** (tu teléfono,
  tu Neon). No repartas la cadena a otros usuarios: no les estarías dando “sus
  datos”, sino los de todos.
- Nunca metas la cadena en el código ni en el control de versiones.
- Si la cadena se filtra, rótala en Neon (*Reset password* del rol).

Si algún día Caja tiene usuarios que no son el dueño de la base, el camino no
es repartir cadenas: es RLS en Postgres + tokens por usuario (Neon Data API /
Neon RLS), y entonces el modo directo podría dejar de ser de solo lectura sin
regalar la base entera.

## Verificación

Los tests (`./gradlew test`) cubren el mapeo del contrato, el cursor, las
lápidas, el resync, el login con un hash **real** de `node:crypto`, la
traducción de errores de Postgres y el push directo (aritmética del
movimiento, idempotencia, rechazos registrados, ops desconocidas en
`skipped`) más el registro (transacción única, hash scrypt, correo
duplicado).

El SQL en sí no lo cubre un test de la app (no hay Postgres en la JVM). Se
validó aparte:

- sintaxis, con el parser real de Postgres (`pg-query-emscripten`);
- que cada tabla y columna exista en `prisma/schema.prisma`;
- que las claves de cada colección coincidan **una a una** con las que produce
  `serialize.ts`.

Si cambias `NeonSql.kt`, vuelve a pasar esas comprobaciones contra el esquema
del proyecto web.
