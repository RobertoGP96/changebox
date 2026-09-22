package com.lolo.changebox.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Preferencias de la vista de Inicio: qué secciones se muestran, en qué orden,
// qué cuentas lista la sección Cuentas y qué gadgets añadió el usuario.
// Portadas 1:1 de dashboard-prefs.ts. Lógica pura (sin Room ni Compose); en la
// web se guardan en User.dashboardPrefs, aquí en DataStore (null = defaults).

/** Sección fija del dashboard: su clave serializada y su nombre visible. */
data class DashboardSection(val key: String, val labelEs: String)

/** Secciones fijas del dashboard en su orden por defecto. */
val DASHBOARD_SECTIONS: List<DashboardSection> = listOf(
    DashboardSection("quickActions", "Accesos rápidos"),
    DashboardSection("widgetPanel", "Panel de gadgets"),
    DashboardSection("monthlyChart", "Ingresos vs gastos"),
    DashboardSection("topCategories", "Top gastos del mes"),
    DashboardSection("upcomingInstallments", "Próximas cuotas"),
    DashboardSection("accounts", "Cuentas"),
)

/** Tipos de gadget instanciables (varios por tipo, cada uno con su config). */
@Serializable
enum class WidgetType(val code: String, val labelEs: String) {
    @SerialName("accountCard")
    ACCOUNT_CARD("accountCard", "Tarjeta de cuenta"),

    @SerialName("currencyTotals")
    CURRENCY_TOTALS("currencyTotals", "Totales por moneda"),

    @SerialName("ratePair")
    RATE_PAIR("ratePair", "Tasa de cambio"),

    @SerialName("incomeCard")
    INCOME_CARD("incomeCard", "Resumen de ingresos"),
}

val WIDGET_TYPES: List<WidgetType> = WidgetType.entries

/** Tamaño del gadget dentro del panel bento (columnas que ocupa). */
@Serializable
enum class WidgetSize(val code: String, val labelEs: String) {
    @SerialName("sm")
    SM("sm", "Pequeño"),

    @SerialName("md")
    MD("md", "Mediano"),

    @SerialName("lg")
    LG("lg", "Grande"),
}

val WIDGET_SIZES: List<WidgetSize> = WidgetSize.entries

val DEFAULT_WIDGET_SIZE: Map<WidgetType, WidgetSize> = mapOf(
    WidgetType.ACCOUNT_CARD to WidgetSize.SM,
    WidgetType.CURRENCY_TOTALS to WidgetSize.MD,
    WidgetType.RATE_PAIR to WidgetSize.SM,
    WidgetType.INCOME_CARD to WidgetSize.MD,
)

// Configuración del gadget «Resumen de ingresos» (tarjeta con gráfico).

@Serializable
enum class IncomeCardVariant(val code: String, val labelEs: String) {
    @SerialName("soft")
    SOFT("soft", "Suave"),

    @SerialName("dark")
    DARK("dark", "Oscura"),
}

val INCOME_CARD_VARIANTS: List<IncomeCardVariant> = IncomeCardVariant.entries

@Serializable
enum class IncomeCardMetric(val code: String, val labelEs: String) {
    @SerialName("income")
    INCOME("income", "Ingresos"),

    @SerialName("expense")
    EXPENSE("expense", "Gastos"),

    @SerialName("net")
    NET("net", "Neto"),
}

val INCOME_CARD_METRICS: List<IncomeCardMetric> = IncomeCardMetric.entries

@Serializable
enum class IncomeCardPeriod(val code: String, val labelEs: String) {
    @SerialName("day")
    DAY("day", "Día"),

    @SerialName("week")
    WEEK("week", "Semana"),

    @SerialName("month")
    MONTH("month", "Mes"),
}

val INCOME_CARD_PERIODS: List<IncomeCardPeriod> = IncomeCardPeriod.entries

@Serializable
data class DashboardWidget(
    val id: String,
    val type: WidgetType,
    /** Columnas que ocupa en el panel bento. */
    val size: WidgetSize,
    /** accountCard: cuenta de la tarjeta. incomeCard: filtro (null = todas). */
    val accountId: String? = null,
    /** accountCard: incluir los últimos movimientos. */
    val showMovements: Boolean? = null,
    /** accountCard: incluir denominaciones disponibles (solo cajas). */
    val showDenominations: Boolean? = null,
    /** ratePair: par origen→destino. */
    val fromCurrencyId: String? = null,
    val toCurrencyId: String? = null,
    /** incomeCard: variante visual. */
    val variant: IncomeCardVariant? = null,
    /** incomeCard: serie que dibuja el gráfico. */
    val metric: IncomeCardMetric? = null,
    /** incomeCard: periodo inicial de los tabs. */
    val defaultPeriod: IncomeCardPeriod? = null,
    /** incomeCard: título propio (null = automático). */
    val title: String? = null,
    /** incomeCard: mostrar los tabs Día/Semana/Mes. */
    val showTabs: Boolean? = null,
    /** incomeCard: variación vs el periodo anterior. */
    val showDelta: Boolean? = null,
    /** incomeCard: métricas del pie de la tarjeta. */
    val showIncome: Boolean? = null,
    val showExpense: Boolean? = null,
    val showNet: Boolean? = null,
)

@Serializable
data class DashboardSectionPref(
    /** Clave fija de DASHBOARD_SECTIONS. */
    val key: String,
    val visible: Boolean,
)

@Serializable
data class DashboardPrefs(
    val sections: List<DashboardSectionPref>,
    val widgets: List<DashboardWidget>,
    /** Cuentas visibles en la sección Cuentas; null = todas. */
    val accountIds: List<String>? = null,
)

const val MAX_WIDGETS = 12

/** Tope defensivo de cuentas fijadas (mismo número que la web). */
private const val MAX_ACCOUNT_IDS = 100

/** Largo máximo de los ids que aceptamos de un origen no confiable. */
private const val MAX_ID_LENGTH = 40

private val FIXED_KEYS: Set<String> = DASHBOARD_SECTIONS.map { it.key }.toSet()

// ignoreUnknownKeys: unas prefs de una versión futura no deben romper la app.
// explicitNulls = false imita a JSON.stringify de la web, que omite undefined.
private val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Todas las secciones visibles, sin gadgets, en el orden canónico. */
fun defaultDashboardPrefs(): DashboardPrefs = DashboardPrefs(
    sections = DASHBOARD_SECTIONS.map { DashboardSectionPref(it.key, visible = true) },
    widgets = emptyList(),
    accountIds = null,
)

/** Contenido si el elemento es un string JSON de verdad; null si no lo es. */
private fun JsonElement?.asJsonString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

/** String no vacío y de largo acotado (el `str()` de la web). */
private fun JsonElement?.asShortString(): String? =
    asJsonString()?.takeIf { it.isNotEmpty() && it.length <= MAX_ID_LENGTH }

// Los flags se comparan contra el literal booleano: un "true" en string NO
// cuenta, igual que los `=== true` / `!== false` de la web.
private val JSON_TRUE = JsonPrimitive(true)
private val JSON_FALSE = JsonPrimitive(false)

private fun JsonElement?.isExactlyTrue(): Boolean = this == JSON_TRUE

private fun JsonElement?.isNotExactlyFalse(): Boolean = this != JSON_FALSE

private fun normalizeWidget(item: JsonElement): DashboardWidget? {
    val raw = item as? JsonObject ?: return null
    val id = raw["id"].asJsonString() ?: return null
    if (id.isEmpty() || id.length > MAX_ID_LENGTH) return null
    val typeCode = raw["type"].asJsonString() ?: return null
    val type = WIDGET_TYPES.firstOrNull { it.code == typeCode } ?: return null
    val sizeCode = raw["size"].asJsonString()
    val size = WIDGET_SIZES.firstOrNull { it.code == sizeCode }
        ?: DEFAULT_WIDGET_SIZE.getValue(type)

    return when (type) {
        WidgetType.ACCOUNT_CARD -> {
            val accountId = raw["accountId"].asShortString() ?: return null
            DashboardWidget(
                id = id,
                type = type,
                size = size,
                accountId = accountId,
                showMovements = raw["showMovements"].isExactlyTrue(),
                showDenominations = raw["showDenominations"].isExactlyTrue(),
            )
        }

        WidgetType.RATE_PAIR -> {
            val fromCurrencyId = raw["fromCurrencyId"].asShortString()
            val toCurrencyId = raw["toCurrencyId"].asShortString()
            if (fromCurrencyId == null || toCurrencyId == null ||
                fromCurrencyId == toCurrencyId
            ) {
                return null
            }
            DashboardWidget(
                id = id,
                type = type,
                size = size,
                fromCurrencyId = fromCurrencyId,
                toCurrencyId = toCurrencyId,
            )
        }

        WidgetType.INCOME_CARD -> {
            val variantCode = raw["variant"].asJsonString()
            val metricCode = raw["metric"].asJsonString()
            val periodCode = raw["defaultPeriod"].asJsonString()
            DashboardWidget(
                id = id,
                type = type,
                size = size,
                accountId = raw["accountId"].asShortString(),
                variant = INCOME_CARD_VARIANTS.firstOrNull { it.code == variantCode }
                    ?: IncomeCardVariant.SOFT,
                metric = INCOME_CARD_METRICS.firstOrNull { it.code == metricCode }
                    ?: IncomeCardMetric.INCOME,
                defaultPeriod = INCOME_CARD_PERIODS.firstOrNull { it.code == periodCode }
                    ?: IncomeCardPeriod.MONTH,
                title = raw["title"].asShortString(),
                showTabs = raw["showTabs"].isNotExactlyFalse(),
                showDelta = raw["showDelta"].isNotExactlyFalse(),
                showIncome = raw["showIncome"].isNotExactlyFalse(),
                showExpense = raw["showExpense"].isNotExactlyFalse(),
                showNet = raw["showNet"].isNotExactlyFalse(),
            )
        }

        WidgetType.CURRENCY_TOTALS -> DashboardWidget(id = id, type = type, size = size)
    }
}

/**
 * Normaliza preferencias de origen no confiable: descarta claves y gadgets
 * inválidos o duplicados, y añade al final (visibles) las secciones fijas que
 * falten — así unas preferencias de una versión vieja no ocultan secciones
 * nuevas. Los gadgets viven en el panel bento y su orden es el de la lista
 * `widgets`; las claves "widget:<id>" de versiones anteriores (gadgets como
 * secciones sueltas) se descartan sin perder los gadgets.
 */
fun normalizeDashboardPrefs(raw: JsonElement?): DashboardPrefs {
    // Cualquier cosa que no sea un objeto (null, "x", un número) equivale a {}.
    val source = raw as? JsonObject ?: JsonObject(emptyMap())

    val widgets = mutableListOf<DashboardWidget>()
    val widgetIds = mutableSetOf<String>()
    val rawWidgets = source["widgets"] as? JsonArray
    if (rawWidgets != null) {
        for (item in rawWidgets) {
            if (widgets.size >= MAX_WIDGETS) break
            val widget = normalizeWidget(item) ?: continue
            if (!widgetIds.add(widget.id)) continue
            widgets.add(widget)
        }
    }

    val sections = mutableListOf<DashboardSectionPref>()
    val seen = mutableSetOf<String>()
    val rawSections = source["sections"] as? JsonArray
    if (rawSections != null) {
        for (item in rawSections) {
            val obj = item as? JsonObject ?: continue
            val key = obj["key"].asJsonString() ?: continue
            if (key in seen) continue
            if (key !in FIXED_KEYS) continue
            seen.add(key)
            sections.add(DashboardSectionPref(key, visible = obj["visible"].isNotExactlyFalse()))
        }
    }
    for (section in DASHBOARD_SECTIONS) {
        if (section.key !in seen) {
            sections.add(DashboardSectionPref(section.key, visible = true))
        }
    }

    var accountIds: List<String>? = null
    val rawAccountIds = source["accountIds"] as? JsonArray
    if (rawAccountIds != null) {
        val ids = mutableListOf<String>()
        for (item in rawAccountIds) {
            if (ids.size >= MAX_ACCOUNT_IDS) break
            val id = item.asShortString() ?: continue
            if (id in ids) continue
            ids.add(id)
        }
        accountIds = ids
    }

    return DashboardPrefs(sections = sections, widgets = widgets, accountIds = accountIds)
}

/** Lee las preferencias desde el JSON guardado; nunca lanza. */
fun parseDashboardPrefs(stored: String?): DashboardPrefs {
    if (stored.isNullOrEmpty()) return defaultDashboardPrefs()
    return try {
        normalizeDashboardPrefs(JSON.parseToJsonElement(stored))
    } catch (e: SerializationException) {
        defaultDashboardPrefs()
    } catch (e: IllegalArgumentException) {
        defaultDashboardPrefs()
    }
}

/** Serializa preferencias (ya normalizadas) para guardarlas. */
fun serializeDashboardPrefs(prefs: DashboardPrefs): String =
    JSON.encodeToString(DashboardPrefs.serializer(), prefs)
