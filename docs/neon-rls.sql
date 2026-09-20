-- ============================================================================
-- Neon RLS para Caja — aislamiento por usuario con JWT (auth.user_id()).
--
-- Ejecutar COMO OWNER (neondb_owner) y SOLO DESPUÉS de configurar Neon RLS
-- en la consola (JWKS del proveedor de identidad): las políticas llaman a
-- auth.user_id(), que instala pg_session_jwt al configurar RLS/Data API.
--
-- Qué cambia y qué no:
--   · El proyecto web (Prisma, cadena de owner) NO se ve afectado: el dueño
--     de las tablas ignora RLS (no usamos FORCE).
--   · El modo "Neon directo" actual del APK con la cadena de owner tampoco.
--   · El rol `authenticated` (cadena SIN contraseña; el JWT es la
--     credencial) solo ve y toca las filas de su propio usuario.
--
-- Idempotente: se puede re-ejecutar (IF NOT EXISTS / OR REPLACE / DROP+CREATE
-- de políticas).
-- ============================================================================

-- pg_session_jwt lo instala Neon al configurar RLS; por si acaso:
CREATE EXTENSION IF NOT EXISTS pg_session_jwt;

-- ── Vínculo User ↔ identidad JWT ────────────────────────────────────────────
-- auth.user_id() devuelve el `sub` del JWT (el id del proveedor de
-- identidad, p. ej. Stack Auth). Se guarda en User."authId".

ALTER TABLE caja."User" ADD COLUMN IF NOT EXISTS "authId" text;
CREATE UNIQUE INDEX IF NOT EXISTS "User_authId_key" ON caja."User" ("authId");

-- Id de usuario de Caja del JWT actual (NULL si aún no está vinculado).
CREATE OR REPLACE FUNCTION caja.current_app_user_id() RETURNS text
LANGUAGE sql STABLE
AS $$
  SELECT u."id" FROM caja."User" u WHERE u."authId" = auth.user_id()
$$;

-- ── Permisos del rol authenticated ──────────────────────────────────────────
-- Session queda fuera a propósito: el modo JWT no usa sesiones propias y ese
-- token hasheado no le incumbe a nadie más que al owner.

GRANT USAGE ON SCHEMA caja TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON
  caja."User", caja."Currency", caja."Denomination", caja."AccountGroup",
  caja."Account", caja."Category", caja."Transaction",
  caja."TransactionDenomination", caja."ExchangeRate", caja."CashCount",
  caja."CashCountLine", caja."Contact", caja."Debt", caja."PaymentPlan",
  caja."Installment", caja."DebtPayment", caja."SyncTombstone",
  caja."SyncOperation"
TO authenticated;
GRANT EXECUTE ON FUNCTION caja.current_app_user_id() TO authenticated;

-- ── RLS ─────────────────────────────────────────────────────────────────────

ALTER TABLE caja."User"                    ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Currency"                ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Denomination"            ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."AccountGroup"            ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Account"                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Category"                ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Transaction"             ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."TransactionDenomination" ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."ExchangeRate"            ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."CashCount"               ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."CashCountLine"           ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Contact"                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Debt"                    ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."PaymentPlan"             ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."Installment"             ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."DebtPayment"             ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."SyncTombstone"           ENABLE ROW LEVEL SECURITY;
ALTER TABLE caja."SyncOperation"           ENABLE ROW LEVEL SECURITY;
-- Sin políticas ni grants: para authenticated es deny-all.
ALTER TABLE caja."Session"                 ENABLE ROW LEVEL SECURITY;

-- ── Políticas ───────────────────────────────────────────────────────────────

-- User: cada identidad ve/edita SU fila y puede crearla (primer login =
-- registro autoservicio: inserta su User con su propio authId).
DROP POLICY IF EXISTS user_select ON caja."User";
CREATE POLICY user_select ON caja."User" FOR SELECT TO authenticated
  USING ("authId" = auth.user_id());
DROP POLICY IF EXISTS user_insert ON caja."User";
CREATE POLICY user_insert ON caja."User" FOR INSERT TO authenticated
  WITH CHECK ("authId" = auth.user_id());
DROP POLICY IF EXISTS user_update ON caja."User";
CREATE POLICY user_update ON caja."User" FOR UPDATE TO authenticated
  USING ("authId" = auth.user_id())
  WITH CHECK ("authId" = auth.user_id());

-- Tablas con "userId" directo: la fila es mía o no existe para mí.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'Currency', 'AccountGroup', 'Account', 'Category', 'Transaction',
    'ExchangeRate', 'CashCount', 'Contact', 'Debt', 'PaymentPlan',
    'SyncTombstone', 'SyncOperation'
  ] LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON caja.%I',
                   lower(t) || '_isolation', t);
    EXECUTE format(
      'CREATE POLICY %I ON caja.%I FOR ALL TO authenticated
         USING ("userId" = caja.current_app_user_id())
         WITH CHECK ("userId" = caja.current_app_user_id())',
      lower(t) || '_isolation', t);
  END LOOP;
END $$;

-- Tablas SIN "userId": se acotan por su fila dueña (misma lógica que usa el
-- pull de la app y serialize.ts del servidor).

DROP POLICY IF EXISTS denomination_isolation ON caja."Denomination";
CREATE POLICY denomination_isolation ON caja."Denomination" FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM caja."Currency" c
    WHERE c."id" = "Denomination"."currencyId"
      AND c."userId" = caja.current_app_user_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM caja."Currency" c
    WHERE c."id" = "Denomination"."currencyId"
      AND c."userId" = caja.current_app_user_id()));

DROP POLICY IF EXISTS txdenomination_isolation ON caja."TransactionDenomination";
CREATE POLICY txdenomination_isolation ON caja."TransactionDenomination" FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM caja."Transaction" t
    WHERE t."id" = "TransactionDenomination"."transactionId"
      AND t."userId" = caja.current_app_user_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM caja."Transaction" t
    WHERE t."id" = "TransactionDenomination"."transactionId"
      AND t."userId" = caja.current_app_user_id()));

DROP POLICY IF EXISTS cashcountline_isolation ON caja."CashCountLine";
CREATE POLICY cashcountline_isolation ON caja."CashCountLine" FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM caja."CashCount" cc
    WHERE cc."id" = "CashCountLine"."cashCountId"
      AND cc."userId" = caja.current_app_user_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM caja."CashCount" cc
    WHERE cc."id" = "CashCountLine"."cashCountId"
      AND cc."userId" = caja.current_app_user_id()));

DROP POLICY IF EXISTS installment_isolation ON caja."Installment";
CREATE POLICY installment_isolation ON caja."Installment" FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM caja."PaymentPlan" p
    WHERE p."id" = "Installment"."planId"
      AND p."userId" = caja.current_app_user_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM caja."PaymentPlan" p
    WHERE p."id" = "Installment"."planId"
      AND p."userId" = caja.current_app_user_id()));

DROP POLICY IF EXISTS debtpayment_isolation ON caja."DebtPayment";
CREATE POLICY debtpayment_isolation ON caja."DebtPayment" FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM caja."Debt" d
    WHERE d."id" = "DebtPayment"."debtId"
      AND d."userId" = caja.current_app_user_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM caja."Debt" d
    WHERE d."id" = "DebtPayment"."debtId"
      AND d."userId" = caja.current_app_user_id()));

-- ── Vincular un usuario EXISTENTE a su identidad JWT (correr como owner) ────
-- El id de Stack Auth aparece en la tabla neon_auth.users_sync (si usas Neon
-- Auth) o en el panel del proveedor:
--
--   UPDATE caja."User" SET "authId" = '<sub del JWT>'
--   WHERE "email" = 'tu@correo.com';
