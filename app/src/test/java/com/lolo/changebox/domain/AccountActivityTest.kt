package com.lolo.changebox.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Vectores portados de account-activity.test.ts. Ojo: allí los meses de `new
// Date(y, m, d)` son base 0 (8 = septiembre); aquí van con su número real.

private fun ms(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 0,
    minute: Int = 0,
): Long = LocalDateTime.of(year, month, day, hour, minute)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private fun fechaLocal(t: Long): LocalDateTime =
    Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun movimiento(
    kind: String,
    accountId: String = "propia",
    counterAccountId: String? = null,
    amountMinor: Long = 1000L,
    counterAmountMinor: Long? = null,
    occurredAtMillis: Long = ms(2026, 9, 7, 12),
) = AccountLedgerEntry(
    accountId = accountId,
    counterAccountId = counterAccountId,
    kind = kind,
    amountMinor = amountMinor,
    counterAmountMinor = counterAmountMinor,
    occurredAtMillis = occurredAtMillis,
)

class AccountDeltaMinorTest {
    @Test
    fun `lado propio INCOME suma, EXPENSE y TRANSFER restan`() {
        assertEquals(1000L, accountDeltaMinor("propia", movimiento("INCOME")))
        assertEquals(-1000L, accountDeltaMinor("propia", movimiento("EXPENSE")))
        assertEquals(-1000L, accountDeltaMinor("propia", movimiento("TRANSFER")))
    }

    @Test
    fun `ADJUSTMENT conserva su signo`() {
        assertEquals(
            -700L,
            accountDeltaMinor("propia", movimiento("ADJUSTMENT", amountMinor = -700L)),
        )
    }

    @Test
    fun `transferencia entrante suma counterAmountMinor en la moneda de la destino`() {
        val transfer = movimiento(
            kind = "TRANSFER",
            accountId = "origen",
            counterAccountId = "destino",
            counterAmountMinor = 2500L,
        )
        assertEquals(2500L, accountDeltaMinor("destino", transfer))
    }

    @Test
    fun `como contraparte de un no TRANSFER no aporta nada`() {
        val multiMoneda = movimiento(
            kind = "INCOME",
            accountId = "origen",
            counterAccountId = "otra",
            counterAmountMinor = 999L,
        )
        assertEquals(0L, accountDeltaMinor("otra", multiMoneda))
    }

    @Test
    fun `un movimiento ajeno no aporta nada`() {
        assertEquals(0L, accountDeltaMinor("ajena", movimiento("INCOME")))
    }
}

class ActivityDeltasTest {
    @Test
    fun `mapea a epoch millis y omite deltas nulos`() {
        val cuando = ms(2026, 9, 1, 9, 30)
        val deltas = activityDeltas(
            "propia",
            listOf(
                movimiento("INCOME", occurredAtMillis = cuando, amountMinor = 500L),
                movimiento("INCOME", accountId = "ajena"),
                movimiento("EXPENSE", occurredAtMillis = cuando, amountMinor = 200L),
            ),
        )
        assertEquals(
            listOf(ActivityDelta(cuando, 500L), ActivityDelta(cuando, -200L)),
            deltas,
        )
    }
}

class CalendarioActividadTest {
    @Test
    fun `startOfDay y startOfMonth truncan en local`() {
        val t = ms(2026, 9, 7, 23, 59)
        assertEquals(0, fechaLocal(startOfDay(t)).hour)
        assertEquals(1, fechaLocal(startOfMonth(t)).dayOfMonth)
        assertEquals(9, fechaLocal(startOfMonth(t)).monthValue)
    }

    @Test
    fun `startOfWeek cae en lunes`() {
        // 7 sep 2026 es lunes; el domingo 13 pertenece a esa misma semana.
        val lunes = ms(2026, 9, 7)
        assertEquals(lunes, startOfWeek(ms(2026, 9, 13, 20)))
        assertEquals(lunes, startOfWeek(lunes))
    }

    @Test
    fun `addDays y addMonths usan calendario incluido el fin de mes`() {
        assertEquals(2, fechaLocal(addDays(ms(2026, 1, 31), 1)).monthValue)
        val feb = addMonths(ms(2026, 1, 15), 1)
        assertEquals(2, fechaLocal(feb).monthValue)
        assertEquals(1, fechaLocal(feb).dayOfMonth)
    }

    // Extra (no está en el TS): addMonths desde un día 31 no se descuelga al
    // mes siguiente, porque siempre aterriza en el día 1.
    @Test
    fun `addMonths desde fin de mes aterriza en el dia 1 del mes pedido`() {
        val marzo = addMonths(ms(2026, 1, 31), 2)
        assertEquals(3, fechaLocal(marzo).monthValue)
        assertEquals(1, fechaLocal(marzo).dayOfMonth)
    }
}

class BucketRangeTest {
    @Test
    fun `genera n mas 1 bordes diarios que cubren el rango`() {
        val from = ms(2026, 9, 1)
        val to = ms(2026, 9, 7, 15)
        val edges = bucketRange(from, to, ActivityGranularity.DAY)
        assertEquals(8, edges.size) // 7 días → 8 bordes
        assertEquals(from, edges[0])
        assertTrue(edges.last() > to)
    }

    @Test
    fun `bordes mensuales caen en el dia 1 de cada mes`() {
        val from = ms(2026, 1, 1)
        val to = ms(2026, 4, 10)
        val edges = bucketRange(from, to, ActivityGranularity.MONTH)
        assertEquals(
            listOf(1, 1, 1, 1, 1),
            edges.map { fechaLocal(it).dayOfMonth },
        )
    }

    // Extra (el TS no prueba la granularidad semanal): los bordes van de 7 en 7.
    @Test
    fun `bordes semanales avanzan siete dias`() {
        val from = ms(2026, 9, 7) // lunes
        val edges = bucketRange(from, ms(2026, 9, 20), ActivityGranularity.WEEK)
        assertEquals(
            listOf(7, 14, 21),
            edges.map { fechaLocal(it).dayOfMonth },
        )
    }
}

class AggregateActivityTest {
    private fun dia(d: Int, hour: Int = 10) = ms(2026, 9, d, hour)

    @Test
    fun `suma entradas y salidas en su bucket e ignora lo de fuera`() {
        val edges = bucketRange(dia(1, 0), dia(2, 23), ActivityGranularity.DAY)
        val buckets = aggregateActivity(
            listOf(
                ActivityDelta(dia(1), 500L),
                ActivityDelta(dia(1, 18), -200L),
                ActivityDelta(dia(2), 300L),
                ActivityDelta(dia(9), 9999L), // fuera del rango
            ),
            edges,
        )
        assertEquals(2, buckets.size)
        assertEquals(500L, buckets[0].inMinor)
        assertEquals(200L, buckets[0].outMinor)
        assertEquals(300L, buckets[1].inMinor)
        assertEquals(0L, buckets[1].outMinor)
    }

    @Test
    fun `un delta en el borde pertenece al bucket que empieza ahi`() {
        val edges = bucketRange(dia(1, 0), dia(2, 23), ActivityGranularity.DAY)
        val buckets = aggregateActivity(listOf(ActivityDelta(dia(2, 0), 100L)), edges)
        assertEquals(0L, buckets[0].inMinor)
        assertEquals(100L, buckets[1].inMinor)
    }

    @Test
    fun `sin bordes suficientes devuelve lista vacia`() {
        assertEquals(
            emptyList<ActivityBucket>(),
            aggregateActivity(listOf(ActivityDelta(dia(1), 1L)), listOf(dia(1))),
        )
    }
}
