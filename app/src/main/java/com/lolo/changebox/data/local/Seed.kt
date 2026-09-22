package com.lolo.changebox.data.local

import com.lolo.changebox.data.local.entity.CategoryEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.local.entity.DenominationEntity

// Datos por defecto de la primera ejecución, portados 1:1 de
// fantastic-eureka/src/lib/user-defaults.ts: monedas con denominaciones
// reales y categorías base.

data class DefaultDenomination(val valueMinor: Long, val kind: String)

private fun bill(valueMinor: Long) = DefaultDenomination(valueMinor, "BILL")
private fun coin(valueMinor: Long) = DefaultDenomination(valueMinor, "COIN")

data class DefaultCurrency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimalPlaces: Int,
    val isBase: Boolean,
    val kind: String,
    val denominations: List<DefaultDenomination>,
)

val DEFAULT_CURRENCIES: List<DefaultCurrency> = listOf(
    DefaultCurrency(
        code = "CUP", name = "Peso cubano", symbol = "$",
        decimalPlaces = 0, isBase = true, kind = "CASH",
        denominations = listOf(
            bill(1000), bill(500), bill(200), bill(100), bill(50),
            bill(20), bill(10), bill(5), bill(3), bill(1),
            coin(3), coin(1),
        ),
    ),
    DefaultCurrency(
        code = "USD", name = "Dólar estadounidense", symbol = "$",
        decimalPlaces = 2, isBase = false, kind = "CASH",
        denominations = listOf(
            bill(10000), bill(5000), bill(2000), bill(1000),
            bill(500), bill(200), bill(100),
            coin(25), coin(10), coin(5), coin(1),
        ),
    ),
    DefaultCurrency(
        code = "EUR", name = "Euro", symbol = "€",
        decimalPlaces = 2, isBase = false, kind = "CASH",
        denominations = listOf(
            bill(50000), bill(20000), bill(10000), bill(5000),
            bill(2000), bill(1000), bill(500),
            coin(200), coin(100), coin(50), coin(20), coin(10), coin(5), coin(2), coin(1),
        ),
    ),
    // MLC es saldo de tarjeta: digital, sin denominaciones físicas.
    DefaultCurrency(
        code = "MLC", name = "Moneda libremente convertible", symbol = "$",
        decimalPlaces = 2, isBase = false, kind = "DIGITAL",
        denominations = emptyList(),
    ),
)

/**
 * Denominaciones básicas para una moneda NUEVA creada por el usuario
 * (serie 1-2-5 genérica). Billetes de 1 a 500 unidades y, si la moneda tiene
 * decimales, las monedas fraccionarias más comunes (se descartan las que no
 * den un entero en unidades menores).
 */
fun basicDenominations(decimalPlaces: Int): List<DefaultDenomination> {
    val factor = generateSequence(1L) { it * 10 }.take(decimalPlaces + 1).last()
    val bills = listOf(500L, 200, 100, 50, 20, 10, 5, 1).map { bill(it * factor) }
    val coins = if (decimalPlaces > 0) {
        // 0.5, 0.25, 0.1, 0.05 en unidades menores; solo las que quedan enteras.
        listOf(50L to 100L, 25L to 100L, 10L to 100L, 5L to 100L)
            .map { (num, den) -> num * factor to den }
            .filter { (scaled, den) -> scaled % den == 0L }
            .map { (scaled, den) -> coin(scaled / den) }
    } else {
        listOf(coin(1))
    }
    return bills + coins
}

val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "Alimentación",
    "Transporte",
    "Hogar",
    "Salud",
    "Servicios",
    "Compras",
    "Otros gastos",
)

val DEFAULT_INCOME_CATEGORIES = listOf(
    "Ventas",
    "Salario",
    "Remesas",
    "Otros ingresos",
)

/**
 * Crea las monedas (con denominaciones) y categorías base si la BD está
 * vacía. Idempotente: se invoca en cada arranque desde ChangeboxApplication.
 */
suspend fun seedDefaultsIfEmpty(db: ChangeboxDatabase) {
    val catalog = db.catalogDao()
    if (catalog.currencyCount() > 0) return

    for (currency in DEFAULT_CURRENCIES) {
        val saved = CurrencyEntity(
            code = currency.code,
            name = currency.name,
            symbol = currency.symbol,
            decimalPlaces = currency.decimalPlaces,
            isBase = currency.isBase,
            kind = currency.kind,
        )
        catalog.insertCurrency(saved)
        if (currency.denominations.isNotEmpty()) {
            catalog.insertDenominations(
                currency.denominations.map {
                    DenominationEntity(
                        currencyId = saved.id,
                        valueMinor = it.valueMinor,
                        kind = it.kind,
                    )
                }
            )
        }
    }

    catalog.insertCategories(
        DEFAULT_EXPENSE_CATEGORIES.map { CategoryEntity(name = it, kind = "EXPENSE") } +
            DEFAULT_INCOME_CATEGORIES.map { CategoryEntity(name = it, kind = "INCOME") }
    )
}


