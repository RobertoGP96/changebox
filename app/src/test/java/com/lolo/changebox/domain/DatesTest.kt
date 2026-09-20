package com.lolo.changebox.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Vectores portados de dates.test.ts (ojo: los meses de JS son 0-based;
// aquí LocalDate usa meses 1-12).

class NextDueDateTest {
    @Test
    fun `ONCE no genera siguiente`() {
        assertNull(nextDueDate(LocalDate.of(2026, 1, 15), Frequency.ONCE))
    }

    @Test
    fun `WEEKLY suma 7 dias`() {
        assertEquals(
            LocalDate.of(2026, 2, 4),
            nextDueDate(LocalDate.of(2026, 1, 28), Frequency.WEEKLY),
        )
    }

    @Test
    fun `BIWEEKLY suma 14 dias`() {
        assertEquals(
            LocalDate.of(2027, 1, 8),
            nextDueDate(LocalDate.of(2026, 12, 25), Frequency.BIWEEKLY),
        )
    }

    @Test
    fun `MONTHLY mantiene el dia del mes`() {
        assertEquals(
            LocalDate.of(2026, 4, 15),
            nextDueDate(LocalDate.of(2026, 3, 15), Frequency.MONTHLY, 15),
        )
    }

    @Test
    fun `MONTHLY con dia 31 hace clamp en meses cortos`() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            nextDueDate(LocalDate.of(2026, 1, 31), Frequency.MONTHLY, 31),
        )
    }

    @Test
    fun `MONTHLY recupera el dia 31 tras el clamp`() {
        assertEquals(
            LocalDate.of(2026, 3, 31),
            nextDueDate(LocalDate.of(2026, 2, 28), Frequency.MONTHLY, 31),
        )
    }

    @Test
    fun `MONTHLY cruza de diciembre a enero`() {
        assertEquals(
            LocalDate.of(2027, 1, 10),
            nextDueDate(LocalDate.of(2026, 12, 10), Frequency.MONTHLY, 10),
        )
    }
}

class DueDaysTest {
    private val now = LocalDate.of(2026, 7, 7)

    @Test
    fun `calcula dias con fechas locales`() {
        assertEquals(0, daysUntil(LocalDate.of(2026, 7, 7), now))
        assertEquals(1, daysUntil(LocalDate.of(2026, 7, 8), now))
        assertEquals(-3, daysUntil(LocalDate.of(2026, 7, 4), now))
    }

    @Test
    fun `marca vencidas solo las de dias anteriores`() {
        assertTrue(isOverdue(LocalDate.of(2026, 7, 6), now))
        assertFalse(isOverdue(LocalDate.of(2026, 7, 7), now))
    }

    @Test
    fun `genera etiquetas humanas`() {
        assertEquals("Vencida hace 3 días", dueLabel(LocalDate.of(2026, 7, 4), now))
        assertEquals("Vencida ayer", dueLabel(LocalDate.of(2026, 7, 6), now))
        assertEquals("Vence hoy", dueLabel(LocalDate.of(2026, 7, 7), now))
        assertEquals("Vence mañana", dueLabel(LocalDate.of(2026, 7, 8), now))
        assertEquals("En 5 días", dueLabel(LocalDate.of(2026, 7, 12), now))
    }
}

