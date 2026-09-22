package com.lolo.changebox.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Vectores portados de plans-core.test.ts.

/** Día de enero de 2026 en epoch millis locales (el TS usa `new Date(2026, 0, d)`). */
private fun enero(day: Int): Long =
    LocalDate.of(2026, 1, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun febrero(day: Int): Long =
    LocalDate.of(2026, 2, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private data class Cuota(
    override val dueAtMillis: Long,
    override val status: String,
) : InstallmentLike

private data class Mensualidad(
    override val description: String,
    override val nextDueAtMillis: Long?,
) : UrgencySortable

class MonthlyEquivalentMinorTest {
    @Test
    fun `deja igual la mensual`() {
        assertEquals(475_000L, monthlyEquivalentMinor(475_000L, "MONTHLY"))
    }

    @Test
    fun `anualiza semanal y quincenal`() {
        // 1 000 × 52 / 12 = 4 333,33 → 4 333
        assertEquals(4_333L, monthlyEquivalentMinor(1_000L, "WEEKLY"))
        // 1 000 × 26 / 12 = 2 166,66 → 2 167
        assertEquals(2_167L, monthlyEquivalentMinor(1_000L, "BIWEEKLY"))
    }

    @Test
    fun `no cuenta las de frecuencia unica ni las desconocidas`() {
        assertEquals(0L, monthlyEquivalentMinor(9_000L, "ONCE"))
        assertEquals(0L, monthlyEquivalentMinor(9_000L, "RANDOM"))
    }

    // Extra (no está en el TS, donde la frecuencia solo puede ser string):
    // la sobrecarga tipada debe dar exactamente lo mismo.
    @Test
    fun `la sobrecarga con enum coincide con la de texto`() {
        assertEquals(
            monthlyEquivalentMinor(1_000L, "WEEKLY"),
            monthlyEquivalentMinor(1_000L, Frequency.WEEKLY),
        )
        assertEquals(0L, monthlyEquivalentMinor(9_000L, Frequency.ONCE))
    }

    // Extra: el redondeo entero debe partir el medio punto como Math.round de
    // JS (hacia +∞), también con importes negativos (ajustes).
    @Test
    fun `redondea el medio punto hacia arriba como la web`() {
        // 6 × 52 / 12 = 26 exacto: sin resto no hay nada que redondear.
        assertEquals(26L, monthlyEquivalentMinor(6L, Frequency.WEEKLY))
        // 3 × 26 / 12 = 6,5 → 7 (JS redondea 6,5 a 7).
        assertEquals(7L, monthlyEquivalentMinor(3L, Frequency.BIWEEKLY))
        // −3 × 26 / 12 = −6,5 → −6 (JS redondea −6,5 a −6).
        assertEquals(-6L, monthlyEquivalentMinor(-3L, Frequency.BIWEEKLY))
    }
}

class DueToneTest {
    @Test
    fun `marca vencidas, inminentes y futuras`() {
        assertEquals(DueTone.DANGER, dueTone(-3))
        assertEquals(DueTone.WARN, dueTone(0))
        assertEquals(DueTone.WARN, dueTone(1))
        assertEquals(DueTone.NEUTRAL, dueTone(2))
    }
}

class NextPendingTest {
    private fun cuota(day: Int, status: String) = Cuota(enero(day), status)

    @Test
    fun `devuelve la pendiente mas proxima ignorando el resto`() {
        val rows = listOf(cuota(20, "PENDING"), cuota(5, "PAID"), cuota(10, "PENDING"))
        assertEquals(enero(10), nextPending(rows)!!.dueAtMillis)
    }

    @Test
    fun `devuelve null si no queda ninguna pendiente`() {
        assertNull(nextPending(listOf(cuota(5, "PAID"), cuota(9, "SKIPPED"))))
    }
}

class MonthlyCommitmentByCurrencyTest {
    private fun plan(
        amountMinor: Long,
        frequency: String,
        currencyId: String,
        active: Boolean = true,
    ) = PlanLike(active, amountMinor, frequency, currencyId)

    @Test
    fun `agrupa por moneda y ordena de mayor a menor`() {
        assertEquals(
            listOf(
                CurrencyCommitment("cup", 150_000L),
                CurrencyCommitment("usd", 2_000L),
            ),
            monthlyCommitmentByCurrency(
                listOf(
                    plan(100_000L, "MONTHLY", "cup"),
                    plan(50_000L, "MONTHLY", "cup"),
                    plan(2_000L, "MONTHLY", "usd"),
                ),
            ),
        )
    }

    @Test
    fun `ignora planes inactivos y de frecuencia unica`() {
        assertEquals(
            emptyList<CurrencyCommitment>(),
            monthlyCommitmentByCurrency(
                listOf(
                    plan(100_000L, "MONTHLY", "cup", active = false),
                    plan(80_000L, "ONCE", "cup"),
                ),
            ),
        )
    }
}

class ComparePlansByUrgencyTest {
    @Test
    fun `pone primero el vencimiento mas cercano y al final los que no tienen`() {
        val rows = listOf(
            Mensualidad("Netflix", febrero(10)),
            Mensualidad("Sin cuotas", null),
            Mensualidad("Renta", enero(3)),
        )
        assertEquals(
            listOf("Renta", "Netflix", "Sin cuotas"),
            rows.sortedWith(PLANS_BY_URGENCY).map { it.description },
        )
    }

    @Test
    fun `desempata por descripcion`() {
        val due = enero(3)
        val rows = listOf(
            Mensualidad("Zumba", due),
            Mensualidad("Agua", due),
        )
        assertEquals(
            listOf("Agua", "Zumba"),
            rows.sortedWith(PLANS_BY_URGENCY).map { it.description },
        )
    }

    // Extra: dos sin vencimiento también se ordenan por descripción (en el TS
    // lo cubre implícitamente el `!==` entre dos null).
    @Test
    fun `dos sin vencimiento se ordenan por descripcion`() {
        val rows = listOf(Mensualidad("Zumba", null), Mensualidad("Agua", null))
        assertEquals(
            listOf("Agua", "Zumba"),
            rows.sortedWith(PLANS_BY_URGENCY).map { it.description },
        )
    }
}
