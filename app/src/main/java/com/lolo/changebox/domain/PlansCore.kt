package com.lolo.changebox.domain

import java.text.Collator
import java.util.Locale

// Lógica pura de mensualidades (planes de cuotas): equivalencia mensual entre
// frecuencias, urgencia de vencimiento y compromiso por moneda.
// Port 1:1 de fantastic-eureka/src/lib/plans-core.ts, con tests espejo en
// PlansCoreTest. Sin Room ni Compose: aquí solo hay aritmética y orden.

/** Repeticiones al año de cada frecuencia. ONCE no es recurrente. */
private fun repeticionesPorAnio(frequency: Frequency): Int = when (frequency) {
    Frequency.ONCE -> 0
    Frequency.WEEKLY -> 52
    Frequency.BIWEEKLY -> 26
    Frequency.MONTHLY -> 12
}

/**
 * Equivalente mensual de una cuota: permite sumar en una sola cifra
 * mensualidades de distinta frecuencia. Las de frecuencia única no son un
 * compromiso recurrente, así que aportan 0.
 *
 * La web hace `Math.round(monto * perYear / 12)`. Aquí NO se usa coma flotante
 * (prohibido para dinero): `Math.round` de JS es `floor(x + 1/2)`, y eso en
 * enteros es `floor((2·n + 12) / 24)` con n = monto × perYear. Math.floorDiv
 * redondea hacia −∞, así que el medio punto negativo cae del mismo lado que en
 * JS (−2,5 → −2), a diferencia de Math.round de Java sobre Double.
 */
fun monthlyEquivalentMinor(amountMinor: Long, frequency: Frequency): Long {
    val perYear = repeticionesPorAnio(frequency)
    if (perYear == 0) return 0L
    val numerador = amountMinor * perYear
    return Math.floorDiv(2L * numerador + 12L, 24L)
}

/**
 * Variante para la frecuencia tal como se guarda en Room (String).
 * Una frecuencia desconocida aporta 0, igual que el `?? 0` de la web.
 */
fun monthlyEquivalentMinor(amountMinor: Long, frequency: String): Long {
    val parsed = Frequency.entries.firstOrNull { it.name == frequency } ?: return 0L
    return monthlyEquivalentMinor(amountMinor, parsed)
}

/**
 * Tono del badge de vencimiento (vencida → DANGER, hoy/mañana → WARN).
 * Es un enum puro a propósito: el dominio no conoce Color de Compose, el
 * mapeo a colores del tema vive en la UI.
 */
enum class DueTone { DANGER, WARN, NEUTRAL }

/** Centraliza el ternario que si no se repetiría en cada vista. */
fun dueTone(days: Int): DueTone = when {
    days < 0 -> DueTone.DANGER
    days <= 1 -> DueTone.WARN
    else -> DueTone.NEUTRAL
}

/**
 * Cuota mínima que necesita `nextPending`. Es una interfaz (y no un data
 * class) para conservar el genérico del TS: quien llama recibe de vuelta SU
 * propia fila (entidad Room, fila de proyección…), no una copia recortada.
 */
interface InstallmentLike {
    /** Vencimiento en epoch millis (la web usa Date; aquí, como en Room, Long). */
    val dueAtMillis: Long

    /** PENDING | PAID | SKIPPED, tal como se almacena. */
    val status: String
}

/** Cuota pendiente más próxima, o null si no queda ninguna. */
fun <T : InstallmentLike> nextPending(installments: List<T>): T? {
    var best: T? = null
    for (inst in installments) {
        if (inst.status != InstallmentStatus.PENDING.name) continue
        val actual = best
        if (actual == null || inst.dueAtMillis < actual.dueAtMillis) best = inst
    }
    return best
}

/** Datos del plan que usa el cálculo de compromiso mensual. */
data class PlanLike(
    val active: Boolean,
    val amountMinor: Long,
    val frequency: String,
    val currencyId: String,
)

/** Total mensual comprometido en una moneda. */
data class CurrencyCommitment(
    val currencyId: String,
    val amountMinor: Long,
)

/**
 * Compromiso mensual por moneda: suma el equivalente mensual de los planes
 * activos y recurrentes, de mayor a menor importe.
 */
fun monthlyCommitmentByCurrency(plans: List<PlanLike>): List<CurrencyCommitment> {
    // LinkedHashMap para que el orden de inserción sea estable y el sort
    // posterior resulte determinista ante importes empatados.
    val totals = LinkedHashMap<String, Long>()
    for (plan in plans) {
        if (!plan.active) continue
        val monthly = monthlyEquivalentMinor(plan.amountMinor, plan.frequency)
        if (monthly == 0L) continue
        totals[plan.currencyId] = (totals[plan.currencyId] ?: 0L) + monthly
    }
    return totals.entries
        .map { CurrencyCommitment(it.key, it.value) }
        .sortedByDescending { it.amountMinor }
}

/** Campos que ordenan la lista de mensualidades. */
interface UrgencySortable {
    /** Próximo vencimiento en epoch millis, o null si no queda cuota pendiente. */
    val nextDueAtMillis: Long?
    val description: String
}

// localeCompare(_, "es") de la web ⇒ Collator español (ordena ñ y acentos como
// es-ES). En un ThreadLocal porque Collator guarda estado interno al comparar
// y las listas se ordenan desde varios hilos (Dispatchers.IO + UI).
private val COLLATOR_ES: ThreadLocal<Collator> =
    ThreadLocal.withInitial { Collator.getInstance(Locale.forLanguageTag("es")) }

/**
 * Orden de la lista de mensualidades: primero las de vencimiento más
 * cercano (las vencidas quedan arriba por ser fechas pasadas), al final las
 * que ya no tienen cuota pendiente. Desempata por descripción.
 */
fun comparePlansByUrgency(a: UrgencySortable, b: UrgencySortable): Int {
    val aDue = a.nextDueAtMillis
    val bDue = b.nextDueAtMillis
    if (aDue != null && bDue != null) {
        // compareTo y no una resta: dos epoch millis lejanos desbordarían Int.
        val diff = aDue.compareTo(bDue)
        if (diff != 0) return diff
    } else if (aDue != bDue) {
        // Aquí al menos uno es null: el que tiene fecha va delante.
        return if (aDue != null) -1 else 1
    }
    // get() es nulable para Kotlin, pero withInitial garantiza instancia.
    return COLLATOR_ES.get()!!.compare(a.description, b.description)
}

/** El mismo criterio listo para `sortedWith`. */
val PLANS_BY_URGENCY: Comparator<UrgencySortable> =
    Comparator { a, b -> comparePlansByUrgency(a, b) }
