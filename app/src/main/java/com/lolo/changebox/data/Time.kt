package com.lolo.changebox.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Conversión fechas ↔ epoch millis. La web guarda las fechas de formulario a
// las 12:00 locales (`T12:00:00`) para esquivar saltos de zona horaria; aquí
// se replica el mismo convenio.

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.atNoonMillis(): Long =
    atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Fecha elegida + hora ACTUAL del reloj. Es lo que usa la web para el
 * `occurredAt` de un movimiento (`register-form.tsx`): así dos movimientos
 * del mismo día conservan el orden en que se registraron, que es el que
 * ordena el historial y el stock por denominación.
 *
 * Ojo: solo para movimientos. Los vencimientos (`dueAt`, `endAt`) siguen a
 * las 12:00 con `atNoonMillis`, igual que en la web.
 */
fun LocalDate.atCurrentTimeMillis(now: LocalTime = LocalTime.now()): Long =
    atTime(now).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.atStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.atEndOfDayMillis(): Long =
    atTime(23, 59, 59, 999_000_000).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

