package com.lolo.changebox.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Actividad de UNA cuenta a partir de su libro mayor (puro, sin Room):
// cada movimiento aporta un delta con signo desde la perspectiva de la cuenta
// (lado propio vía signedKindMinor; transferencia entrante vía
// counterAmountMinor, que ya viene en la moneda de la cuenta destino) y los
// deltas se agrupan por día/semana/mes para el gráfico de barras del detalle.
// Port 1:1 de fantastic-eureka/src/lib/account-activity.ts.

data class AccountLedgerEntry(
    val accountId: String,
    val counterAccountId: String?,
    val kind: String,
    val amountMinor: Long,
    val counterAmountMinor: Long?,
    /** occurredAt en epoch millis (la web usa Date; Room ya guarda Long). */
    val occurredAtMillis: Long,
)

data class ActivityDelta(
    /** Momento del movimiento en epoch millis. */
    val t: Long,
    /** Cambio que el movimiento produce en el saldo de la cuenta (con signo). */
    val deltaMinor: Long,
)

/** Delta con signo de un movimiento desde la perspectiva de `accountId`. */
fun accountDeltaMinor(accountId: String, entry: AccountLedgerEntry): Long {
    if (entry.accountId == accountId) {
        return signedKindMinor(entry.kind, entry.amountMinor)
    }
    // Solo la transferencia entrante suma por el lado de la contraparte, y lo
    // hace con counterAmountMinor (ya en la moneda de la cuenta destino).
    if (entry.counterAccountId == accountId && entry.kind == TransactionKind.TRANSFER.name) {
        return entry.counterAmountMinor ?: 0L
    }
    return 0L
}

/** Deltas cronológicos de la cuenta; omite movimientos que no la afectan. */
fun activityDeltas(accountId: String, entries: List<AccountLedgerEntry>): List<ActivityDelta> {
    val deltas = mutableListOf<ActivityDelta>()
    for (entry in entries) {
        val deltaMinor = accountDeltaMinor(accountId, entry)
        if (deltaMinor == 0L) continue
        deltas.add(ActivityDelta(entry.occurredAtMillis, deltaMinor))
    }
    return deltas
}

enum class ActivityGranularity { DAY, WEEK, MONTH }

data class ActivityBucket(
    /** Inicio del bucket en epoch millis (calendario local). */
    val startT: Long,
    /** Inicio del bucket siguiente (fin exclusivo). */
    val endT: Long,
    /** Suma de deltas positivos (entradas). */
    val inMinor: Long,
    /** Suma de |deltas negativos| (salidas), siempre ≥ 0. */
    val outMinor: Long,
)

// Aritmética de calendario local (no ms fijos: sobrevive cambios de hora).
// La web usa `new Date(y, m, d)`; aquí el equivalente es LocalDate en la zona
// del dispositivo, resuelta en cada llamada por si el usuario la cambia.

private fun fechaLocal(t: Long): LocalDate =
    Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()

private fun inicioDelDia(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun startOfDay(t: Long): Long = inicioDelDia(fechaLocal(t))

/** Lunes como inicio de semana (convención es-ES). */
fun startOfWeek(t: Long): Long {
    val date = fechaLocal(t)
    // DayOfWeek.value es ISO: lunes = 1, domingo = 7.
    val desdeElLunes = (date.dayOfWeek.value - 1).toLong()
    return inicioDelDia(date.minusDays(desdeElLunes))
}

fun startOfMonth(t: Long): Long = inicioDelDia(fechaLocal(t).withDayOfMonth(1))

fun addDays(t: Long, days: Int): Long = inicioDelDia(fechaLocal(t).plusDays(days.toLong()))

/** Como en la web: avanza n meses y aterriza en el día 1 (no conserva el día). */
fun addMonths(t: Long, months: Int): Long =
    inicioDelDia(YearMonth.from(fechaLocal(t)).plusMonths(months.toLong()).atDay(1))

/**
 * Bordes de buckets (ascendentes, n+1 para n buckets) desde `fromT` — que
 * debe venir ya alineado al calendario — hasta cubrir `toT`.
 */
fun bucketRange(fromT: Long, toT: Long, granularity: ActivityGranularity): List<Long> {
    val edges = mutableListOf(fromT)
    var cursor = fromT
    while (cursor <= toT) {
        cursor = when (granularity) {
            ActivityGranularity.MONTH -> addMonths(cursor, 1)
            ActivityGranularity.WEEK -> addDays(cursor, 7)
            ActivityGranularity.DAY -> addDays(cursor, 1)
        }
        edges.add(cursor)
    }
    return edges
}

/** Suma entradas/salidas por bucket; los deltas fuera del rango se ignoran. */
fun aggregateActivity(deltas: List<ActivityDelta>, edges: List<Long>): List<ActivityBucket> {
    val total = edges.size - 1
    if (total <= 0) return emptyList()

    // Acumuladores aparte porque ActivityBucket es inmutable (data class).
    val entradas = LongArray(total)
    val salidas = LongArray(total)

    for (delta in deltas) {
        if (delta.t < edges[0] || delta.t >= edges[total]) continue
        // Búsqueda binaria del último borde ≤ t.
        var lo = 0
        var hi = total - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (edges[mid] <= delta.t) lo = mid else hi = mid - 1
        }
        if (delta.deltaMinor >= 0) entradas[lo] += delta.deltaMinor
        else salidas[lo] += -delta.deltaMinor
    }

    return (0 until total).map { i ->
        ActivityBucket(
            startT = edges[i],
            endT = edges[i + 1],
            inMinor = entradas[i],
            outMinor = salidas[i],
        )
    }
}
