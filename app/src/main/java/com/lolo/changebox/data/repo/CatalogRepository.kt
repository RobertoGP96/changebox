package com.lolo.changebox.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.guarded
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.basicDenominations
import com.lolo.changebox.data.local.entity.AccountGroupEntity
import com.lolo.changebox.data.local.entity.CategoryEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.local.entity.DenominationEntity
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.CurrencyKind
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.isCashLike
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
        kind: CurrencyKind = CurrencyKind.CASH,
    ): ActionResult<String> = guarded("No se pudo crear la moneda") {
        val cleanCode = code.trim().uppercase()
        if (!Regex("^[A-Z0-9]{2,6}$").matches(cleanCode)) {
            return@guarded fail("Código de 2 a 6 letras (ej. USD)")
        }
        if (name.isBlank()) return@guarded fail("El nombre es obligatorio")
        if (symbol.isBlank()) return@guarded fail("El símbolo es obligatorio")
        if (decimalPlaces !in 0..4) return@guarded fail("Decimales fuera de rango")

        return@guarded try {
            // Una moneda de efectivo nace con denominaciones básicas (serie
            // 1-2-5) para que el arqueo funcione desde el primer momento; se
            // ajustan en Monedas. Las digitales no existen en efectivo, así
            // que no se les siembra ninguna.
            val currency = CurrencyEntity(
                code = cleanCode,
                name = name.trim(),
                symbol = symbol.trim(),
                decimalPlaces = decimalPlaces,
                kind = kind.name,
            )
            db.withTransaction {
                dao.insertCurrency(currency)
                if (kind != CurrencyKind.DIGITAL) {
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
            }
            ok(currency.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe una moneda con ese código")
        }
    }

    suspend fun toggleCurrency(currencyId: String): ActionResult<Boolean> = guarded("No se pudo actualizar la moneda") {
        val currency = dao.currencyById(currencyId) ?: return@guarded fail("Moneda no encontrada")
        if (currency.isBase && currency.active) {
            return@guarded fail("La moneda base no se puede desactivar; primero cambia la base")
        }
        dao.setCurrencyActive(currency.id, !currency.active)
        return@guarded ok(!currency.active)
    }

    /**
     * Cambia la clasificación de la moneda. Pasar a DIGITAL exige que la
     * moneda no tenga denominaciones (hay que borrarlas: ocultarlas no basta,
     * y las usadas no se pueden borrar) ni cuentas de efectivo/caja.
     */
    suspend fun setCurrencyKind(
        currencyId: String,
        kind: CurrencyKind,
    ): ActionResult<String> = guarded("No se pudo cambiar la clasificación") {
        val currency = dao.currencyById(currencyId) ?: return@guarded fail("Moneda no encontrada")
        if (currency.kind == kind.name) return@guarded ok(currency.id)

        if (kind == CurrencyKind.DIGITAL) {
            if (dao.denominationCount(currency.id) > 0) {
                return@guarded fail(
                    "La moneda tiene denominaciones: elimínalas antes de marcarla como digital"
                )
            }
            // "Cuentas de efectivo o caja" = los tipos que admiten arqueo
            // físico; la lista sale de isCashLike(), nunca de comparar strings.
            val cashLikeAccounts = dao.accountCountByTypes(
                currency.id,
                AccountType.entries.filter { it.isCashLike() }.map { it.name },
            )
            if (cashLikeAccounts > 0) {
                return@guarded fail(
                    "Hay cuentas de efectivo o caja en esta moneda: no puede ser digital"
                )
            }
        }

        dao.updateCurrency(currency.copy(kind = kind.name))
        return@guarded ok(currency.id)
    }

    suspend fun setBaseCurrency(currencyId: String): ActionResult<String> = guarded("No se pudo cambiar la moneda base") {
        val currency = dao.currencyById(currencyId) ?: return@guarded fail("Moneda no encontrada")
        if (currency.isBase) return@guarded fail("Esa moneda ya es la base")

        db.withTransaction {
            dao.clearBase()
            dao.markBase(currency.id)
        }
        return@guarded ok(currency.id)
    }

    // ── Denominaciones ──────────────────────────────────────────────────────

    suspend fun createDenomination(
        currencyId: String,
        value: String,
        kind: String,
    ): ActionResult<String> = guarded("No se pudo crear la denominación") {
        val currency = dao.currencyById(currencyId) ?: return@guarded fail("Moneda no válida")

        val valueMinor = try {
            parseAmountToMinor(value, currency.toMinor())
        } catch (e: MoneyException) {
            return@guarded fail(e.message ?: "Monto inválido")
        }
        if (valueMinor <= 0) return@guarded fail("El valor debe ser mayor que cero")

        return@guarded try {
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
    ): ActionResult<String> = guarded("No se pudo editar la denominación") {
        val denomination = dao.denominationById(denominationId)
            ?: return@guarded fail("Denominación no encontrada")
        // Cambiar el valor de una denominación ya usada alteraría los arqueos
        // o desgloses guardados (sus líneas la referencian).
        if (dao.denominationUsage(denomination.id) > 0) {
            return@guarded fail("Ya se usó en arqueos o movimientos; ocúltala y crea una nueva")
        }
        val currency = dao.currencyById(denomination.currencyId)
            ?: return@guarded fail("Moneda no válida")

        val valueMinor = try {
            parseAmountToMinor(value, currency.toMinor())
        } catch (e: MoneyException) {
            return@guarded fail(e.message ?: "Monto inválido")
        }
        if (valueMinor <= 0) return@guarded fail("El valor debe ser mayor que cero")

        return@guarded try {
            dao.updateDenomination(denomination.copy(valueMinor = valueMinor, kind = kind))
            ok(denomination.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe esa denominación")
        }
    }

    suspend fun toggleDenomination(denominationId: String): ActionResult<Boolean> = guarded("No se pudo actualizar la denominación") {
        val denomination = dao.denominationById(denominationId)
            ?: return@guarded fail("Denominación no encontrada")
        dao.updateDenomination(denomination.copy(active = !denomination.active))
        return@guarded ok(!denomination.active)
    }

    suspend fun deleteDenomination(denominationId: String): ActionResult<String> = guarded("No se pudo eliminar la denominación") {
        val denomination = dao.denominationById(denominationId)
            ?: return@guarded fail("Denominación no encontrada")
        // Las FK de líneas de arqueo/desglose son RESTRICT: borrar una usada
        // rompería lo guardado. Mejor mensaje amigable que error de BD.
        if (dao.denominationUsage(denomination.id) > 0) {
            return@guarded fail("Se usó en arqueos o movimientos guardados; ocúltala en su lugar")
        }
        dao.deleteDenomination(denomination.id)
        return@guarded ok(denomination.id)
    }

    // ── Categorías ──────────────────────────────────────────────────────────

    suspend fun createCategory(name: String, kind: String): ActionResult<String> = guarded("No se pudo crear la categoría") {
        if (name.isBlank()) return@guarded fail("El nombre es obligatorio")
        return@guarded try {
            val category = CategoryEntity(name = name.trim(), kind = kind)
            dao.insertCategory(category)
            ok(category.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe una categoría con ese nombre")
        }
    }

    suspend fun renameCategory(categoryId: String, name: String): ActionResult<String> = guarded("No se pudo renombrar la categoría") {
        if (name.isBlank()) return@guarded fail("El nombre es obligatorio")
        val category = dao.categoryById(categoryId) ?: return@guarded fail("Categoría no encontrada")
        return@guarded try {
            dao.updateCategory(category.copy(name = name.trim()))
            ok(category.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe una categoría con ese nombre")
        }
    }

    suspend fun toggleCategory(categoryId: String): ActionResult<Boolean> = guarded("No se pudo actualizar la categoría") {
        val category = dao.categoryById(categoryId) ?: return@guarded fail("Categoría no encontrada")
        dao.updateCategory(category.copy(active = !category.active))
        return@guarded ok(!category.active)
    }

    suspend fun deleteCategory(categoryId: String): ActionResult<String> = guarded("No se pudo eliminar la categoría") {
        val category = dao.categoryById(categoryId) ?: return@guarded fail("Categoría no encontrada")
        // Con movimientos asociados no se borra: se perdería la clasificación
        // del historial. Ocultarla la saca de los formularios sin tocar datos.
        if (dao.categoryUsage(category.id) > 0) {
            return@guarded fail("Tiene movimientos asociados; ocúltala en su lugar")
        }
        dao.deleteCategory(category.id)
        return@guarded ok(category.id)
    }

    // ── Grupos de cuentas ───────────────────────────────────────────────────

    suspend fun createGroup(name: String): ActionResult<String> = guarded("No se pudo crear el grupo") {
        if (name.isBlank()) return@guarded fail("El nombre es obligatorio")
        return@guarded try {
            val group = AccountGroupEntity(name = name.trim())
            dao.insertGroup(group)
            ok(group.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe un grupo con ese nombre")
        }
    }

    suspend fun renameGroup(groupId: String, name: String): ActionResult<String> = guarded("No se pudo renombrar el grupo") {
        if (name.isBlank()) return@guarded fail("El nombre es obligatorio")
        val group = dao.groupById(groupId) ?: return@guarded fail("Grupo no encontrado")
        return@guarded try {
            dao.updateGroup(group.copy(name = name.trim()))
            ok(group.id)
        } catch (e: SQLiteConstraintException) {
            fail("Ya existe un grupo con ese nombre")
        }
    }

    suspend fun deleteGroup(groupId: String): ActionResult<String> = guarded("No se pudo eliminar el grupo") {
        dao.groupById(groupId) ?: return@guarded fail("Grupo no encontrado")
        // SET_NULL deja las cuentas del grupo como "Sin grupo".
        dao.deleteGroup(groupId)
        return@guarded ok(groupId)
    }

    suspend fun assignGroup(accountId: String, groupId: String?): ActionResult<String> = guarded("No se pudo asignar el grupo") {
        if (groupId != null && dao.groupById(groupId) == null) {
            return@guarded fail("Grupo no válido")
        }
        val account = db.accountDao().accountById(accountId)
            ?: return@guarded fail("Cuenta no encontrada")
        db.accountDao().setGroup(account.id, groupId)
        return@guarded ok(account.id)
    }
}

/** Vista mínima de moneda para la aritmética de money.kt. */
fun CurrencyEntity.toMinor() = com.lolo.changebox.domain.MinorCurrencyOf(decimalPlaces)

fun CurrencyEntity.toDisplay() = com.lolo.changebox.domain.DisplayCurrencyOf(code, decimalPlaces)


