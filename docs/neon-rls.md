# Neon RLS (JWT): aislamiento real por usuario, sin servidor

Objetivo: que un usuario del APK pueda registrarse, leer y sincronizar
**directo contra Neon** sin recibir la cadena del dueño de la base. Con RLS
activado, cada identidad solo ve y toca **sus** filas; la cadena que se
reparte (rol `authenticated`) no lleva contraseña — la credencial es un JWT
que el proveedor de identidad emite por usuario.

Por qué RLS "a secas" no bastaba: la app conectaba como **owner**, y el dueño
de las tablas ignora las políticas RLS. La frontera real exige conectar con
otra identidad, y eso es exactamente lo que monta esta guía.

## Las piezas

```
APK ──(JWT de Stack Auth en Authorization: Bearer)──> Neon /sql (rol authenticated)
                                                        │ pg_session_jwt valida el JWT
                                                        │ auth.user_id() = sub del token
                                                        └ políticas RLS por userId
Web (Prisma, cadena owner) ──────────────────────────> sin cambios: el owner ignora RLS
```

- **Proveedor de identidad**: lo más directo es **Neon Auth** (Stack Auth
  gestionado por Neon, gratis): da altas/logins por correo+contraseña, emite
  los JWT y publica el JWKS. Vale cualquier proveedor con JWKS (Clerk,
  Auth0, Firebase…).
- **Neon RLS**: valida los JWT contra ese JWKS dentro de Postgres
  (`pg_session_jwt`) y expone `auth.user_id()` a las políticas.
- **[neon-rls.sql](neon-rls.sql)**: las políticas para el esquema `caja`
  (columna `User."authId"` que vincula el usuario de Caja con el `sub` del
  JWT, permisos del rol `authenticated` y aislamiento por `userId` en todas
  las tablas, con las tablas hijas acotadas por su fila dueña).

## Activación (pasos de consola — solo puedes hacerlos tú)

1. **Neon Console → tu proyecto → Auth**: habilita **Neon Auth**. Apunta:
   - el **Project ID** y la **Publishable client key** de Stack Auth,
   - la **JWKS URL** (forma
     `https://api.stack-auth.com/api/v1/projects/<project-id>/.well-known/jwks.json`).
2. **Neon Console → RLS** (según versión de la consola, "RLS"/"Authorize"):
   añade el proveedor pegando esa **JWKS URL**.
3. Copia la **cadena de conexión del rol `authenticated`** (aparece al
   configurar RLS; no lleva contraseña).
4. Ejecuta [neon-rls.sql](neon-rls.sql) **como owner** (SQL Editor de la
   consola, o `psql` con tu cadena de siempre). Es idempotente.
5. Vincula tu usuario existente (una vez, como owner):

   ```sql
   -- el id (sub) aparece en neon_auth.users_sync tras crear tu usuario
   -- en Neon Auth con el mismo correo
   UPDATE caja."User" SET "authId" = '<sub>' WHERE "email" = 'tu@correo.com';
   ```

6. Mantén el `schema.prisma` del proyecto web al día para evitar drift:

   ```prisma
   model User {
     // …
     authId String? @unique
   }
   ```

Nada de esto rompe lo que ya funciona: la web (owner) y el modo Neon directo
actual con cadena de owner siguen igual. Las políticas solo gobiernan al rol
`authenticated`.

## Lo que falta en la app (siguiente fase)

Con los tres valores de arriba (Project ID, Publishable key, cadena
`authenticated`) se añade a la app el submodo **"Neon RLS"**:

- login/registro contra la API de Stack Auth (correo + contraseña → JWT de
  acceso + token de refresco);
- `NeonHttpClient` mandando `Authorization: Bearer <jwt>` (mismo mecanismo
  del driver serverless);
- registro autoservicio: el primer login inserta su fila en `User` (la
  política `user_insert` lo permite solo con su propio `authId`) y siembra su
  catálogo;
- sin tabla `Session` ni scrypt en este submodo: la sesión la gestiona el
  proveedor.

## Seguridad, en corto

- La cadena de **owner** sigue siendo la llave de toda la base: no se
  comparte nunca. Con RLS activado deja de ser necesaria para otros usuarios.
- La cadena `authenticated` puede repartirse: sin un JWT válido no lee ni una
  fila, y con JWT solo las del propio usuario.
- `Session` queda deny-all para `authenticated`: los tokens hasheados del
  modo owner no son visibles.
