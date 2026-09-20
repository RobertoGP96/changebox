package com.lolo.changebox.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// Recurrencia de cuotas y etiquetas de vencimiento, portadas de dates.ts
// sobre java.time (la web usa Date; la semántica de fechas locales es la misma).

/**
 * Próxima fecha de vencimiento a partir de la cuota actual.
 * MONTHLY respeta dayOfMonth con clamp al último día del mes (31 → 28/29 feb),
 * por eso el día objetivo se guarda en el plan y no se deriva de la cuota.
 * Devuelve null para frecuencia ONCE.
 */
fun nextDueDate(
    current: LocalDate,
    frequency: Frequency,
    dayOfMonth: Int? = null,
): LocalDate? = when (frequency) {
    Frequency.ONCE -> null
    Frequency.WEEKLY -> current.plusDays(7)
    Frequency.BIWEEKLY -> current.plusDays(14)
    Frequency.MONTHLY -> {
        val target = YearMonth.from(current).plusMonths(1)
        val day = minOf(dayOfMonth ?: current.dayOfMonth, target.lengthOfMonth())
        target.atDay(day)
    }
}

/** Días (con signo) desde hoy hasta dueAt comparando fechas locales. */
fun daysUntil(dueAt: LocalDate, now: LocalDate = LocalDate.now()): Int =
    ChronoUnit.DAYS.between(now, dueAt).toInt()

fun isOverdue(dueAt: LocalDate, now: LocalDate = LocalDate.now()): Boolean =
    daysUntil(dueAt, now) < 0

/** Etiqueta humana del vencimiento: "Vencida hace 3 días", "Vence hoy", "En 5 días". */
fun dueLabel(dueAt: LocalDate, now: LocalDate = LocalDate.now()): String {
    val days = daysUntil(dueAt, now)
    return when {
        days < -1 -> "Vencida hace ${-days} días"
        days == -1 -> "Vencida ayer"
        days == 0 -> "Vence hoy"
        days == 1 -> "Vence mañana"
        else -> "En $days días"
    }
}

