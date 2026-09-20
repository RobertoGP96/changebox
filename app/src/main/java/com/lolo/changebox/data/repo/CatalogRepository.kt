package com.lolo.changebox.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.basicDenominations
import com.lolo.changebox.data.local.entity.AccountGroupEntity
import com.lolo.changebox.data.local.entity.CategoryEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.local.entity.DenominationEntity
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.parseAmountToMinor

// Núcleo de monedas, denominaciones, categorías y grupos: port 1:1 de
// lib/services/catalog-service.ts. Mismos mensajes de error que la web; las
// violaciones de unicidad (P2002 en Prisma) aquí son SQLiteConstraintException.

class CatalogRepository(private val db: ChangeboxDatabase) {

    private val dao = db.catalogDao()

    // ── Monedas ─────────────────────────────────────────────────────────────

    suspend fun createCurrency(
        code: String,
        name: String,
        symbol: String,
        decimalPlaces: Int,
    ): ActionResult<String> {
        val cleanCode = code.trim().uppercase()
        if (!Regex("^[A-Z0-9]{2,6}$").matches(cleanCode)) {
            return fail("Código de 2 a 6 letras (ej. USD)")
        }
        if (name.isBlank()) return fail("El nombre es obligatorio")
        if (symbol.isBlank()) return fail("El símbolo es obligatorio")
        if (decimalPlaces !in 0..4) return fail("Decimales fuera de rango")

        return try {
            // La moneda nace con denominaciones básicas (serie 1-2-5) para que
            // el arqueo funcione desde el primer momento; se ajustan en Monedas.
            val currency = CurrencyEntity(
                code = cleanCode,
                name = name.trim(),
                symbol = symbol.trim(),
                decimalPlaces = decimalPlaces,
            )
            db.withTransaction {
                dao.insertCurrency(currency)
                dao.insertDenominations(
                    basicDenominations(decimalPlaces).map {
                        DenominationEntity(
                            currencyId = currency.id,
                            valueMinor = it.valueMinor,
                            kind = it.kind,
                        )
                    }
                )
            }
            ok(currency.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe una moneda con ese código")
        }
    }

    suspend fun toggleCurrency(currencyId: String): ActionResult<Boolean> {
        val currency = dao.currencyById(currencyId) ?: return fail("Moneda no encontrada")
        if (currency.isBase && currency.active) {
            return fail("La moneda base no se puede desactivar; primero cambia la base")
        }
        dao.setCurrencyActive(currency.id, !currency.active)
        return ok(!currency.active)
    }

    suspend fun setBaseCurrency(currencyId: String): ActionResult<String> {
        val currency = dao.currencyById(currencyId) ?: return fail("Moneda no encontrada")
        if (currency.isBase) return fail("Esa moneda ya es la base")

        db.withTransaction {
            dao.clearBase()
            dao.markBase(currency.id)
        }
        return ok(currency.id)
    }

    // ── Denominaciones ──────────────────────────────────────────────────────

    suspend fun createDenomination(
        currencyId: String,
        value: String,
        kind: String,
    ): ActionResult<String> {
        val currency = dao.currencyById(currencyId) ?: return fail("Moneda no válida")

        val valueMinor = try {
            parseAmountToMinor(value, currency.toMinor())
        } catch (e: MoneyException) {
            return fail(e.message ?: "Monto inválido")
        }
        if (valueMinor <= 0) return fail("El valor debe ser mayor que cero")

        return try {
            val denomination = DenominationEntity(
                currencyId = currency.id,
                valueMinor = valueMinor,
                kind = kind,
            )
            dao.insertDenomination(denomination)
            ok(denomination.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe esa denominación")
        }
    }

    suspend fun updateDenomination(
        denominationId: String,
        value: String,
        kind: String,
    ): ActionResult<String> {
        val denomination = dao.denominationById(denominationId)
            ?: return fail("Denominación no encontrada")
        // Cambiar el valor de una denominación ya usada alteraría los arqueos
        // o desgloses guardados (sus líneas la referencian).
        if (dao.denominationUsage(denomination.id) > 0) {
            return fail("Ya se usó en arqueos o movimientos; ocúltala y crea una nueva")
        }
        val currency = dao.currencyById(denomination.currencyId)
            ?: return fail("Moneda no válida")

        val valueMinor = try {
            parseAmountToMinor(value, currency.toMinor())
        } catch (e: MoneyException) {
            return fail(e.message ?: "Monto inválido")
        }
        if (valueMinor <= 0) return fail("El valor debe ser mayor que cero")

        return try {
            dao.updateDenomination(denomination.copy(valueMinor = valueMinor, kind = kind))
            ok(denomination.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe esa denominación")
        }
    }

    suspend fun toggleDenomination(denominationId: String): ActionResult<Boolean> {
        val denomination = dao.denominationById(denominationId)
            ?: return fail("Denominación no encontrada")
        dao.updateDenomination(denomination.copy(active = !denomination.active))
        return ok(!denomination.active)
    }

    suspend fun deleteDenomination(denominationId: String): ActionResult<String> {
        val denomination = dao.denominationById(denominationId)
            ?: return fail("Denominación no encontrada")
        // Las FK de líneas de arqueo/desglose son RESTRICT: borrar una usada
        // rompería lo guardado. Mejor mensaje amigable que error de BD.
        if (dao.denominationUsage(denomination.id) > 0) {
            return fail("Se usó en arqueos o movimientos guardados; ocúltala en su lugar")
        }
        dao.deleteDenomination(denomination.id)
        return ok(denomination.id)
    }

    // ── Categorías ──────────────────────────────────────────────────────────

    suspend fun createCategory(name: String, kind: String): ActionResult<String> {
        if (name.isBlank()) return fail("El nombre es obligatorio")
        return try {
            val category = CategoryEntity(name = name.trim(), kind = kind)
            dao.insertCategory(category)
            ok(category.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe una categoría con ese nombre")
        }
    }

    suspend fun renameCategory(categoryId: String, name: String): ActionResult<String> {
        if (name.isBlank()) return fail("El nombre es obligatorio")
        val category = dao.categoryById(categoryId) ?: return fail("Categoría no encontrada")
        return try {
            dao.updateCategory(category.copy(name = name.trim()))
            ok(category.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe una categoría con ese nombre")
        }
    }

    suspend fun toggleCategory(categoryId: String): ActionResult<Boolean> {
        val category = dao.categoryById(categoryId) ?: return fail("Categoría no encontrada")
        dao.updateCategory(category.copy(active = !category.active))
        return ok(!category.active)
    }

    suspend fun deleteCategory(categoryId: String): ActionResult<String> {
        val category = dao.categoryById(categoryId) ?: return fail("Categoría no encontrada")
        // Con movimientos asociados no se borra: se perdería la clasificación
        // del historial. Ocultarla la saca de los formularios sin tocar datos.
        if (dao.categoryUsage(category.id) > 0) {
            return fail("Tiene movimientos asociados; ocúltala en su lugar")
        }
        dao.deleteCategory(category.id)
        return ok(category.id)
    }

    // ── Grupos de cuentas ───────────────────────────────────────────────────

    suspend fun createGroup(name: String): ActionResult<String> {
        if (name.isBlank()) return fail("El nombre es obligatorio")
        return try {
            val group = AccountGroupEntity(name = name.trim())
            dao.insertGroup(group)
            ok(group.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe un grupo con ese nombre")
        }
    }

    suspend fun renameGroup(groupId: String, name: String): ActionResult<String> {
        if (name.isBlank()) return fail("El nombre es obligatorio")
        val group = dao.groupById(groupId) ?: return fail("Grupo no encontrado")
        return try {
            dao.updateGroup(group.copy(name = name.trim()))
            ok(group.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe un grupo con ese nombre")
        }
    }

    suspend fun deleteGroup(groupId: String): ActionResult<String> {
        dao.groupById(groupId) ?: return fail("Grupo no encontrado")
        // SET_NULL deja las cuentas del grupo como "Sin grupo".
        dao.deleteGroup(groupId)
        return ok(groupId)
    }

    suspend fun assignGroup(accountId: String, groupId: String?): ActionResult<String> {
        if (groupId != null && dao.groupById(groupId) == null) {
            return fail("Grupo no válido")
        }
        val account = db.accountDao().accountById(accountId)
            ?: return fail("Cuenta no encontrada")
        db.accountDao().setGroup(account.id, groupId)
        return ok(account.id)
    }
}

/** Vista mínima de moneda para la aritmética de money.kt. */
fun CurrencyEntity.toMinor() = com.lolo.changebox.domain.MinorCurrencyOf(decimalPlaces)

fun CurrencyEntity.toDisplay() = com.lolo.changebox.domain.DisplayCurrencyOf(code, decimalPlaces)


