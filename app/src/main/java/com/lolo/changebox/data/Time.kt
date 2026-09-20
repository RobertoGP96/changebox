package com.lolo.changebox.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Conversión fechas ↔ epoch millis. La web guarda las fechas de formulario a
// las 12:00 locales (`T12:00:00`) para esquivar saltos de zona horaria; aquí
// se replica el mismo convenio.

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.atNoonMillis(): Long =
    atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.atStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.atEndOfDayMillis(): Long =
    atTime(23, 59, 59, 999_000_000).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

