package com.lolo.changebox.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Vectores portados de dashboard-prefs.test.ts. En la web la entrada de
// normalizeDashboardPrefs es `unknown`; aquí es un JsonElement, así que los
// casos se escriben como JSON crudo.
private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

class DefaultDashboardPrefsTest {
    @Test
    fun `incluye todas las secciones visibles en orden canonico, sin gadgets`() {
        val prefs = defaultDashboardPrefs()
        assertEquals(DASHBOARD_SECTIONS.map { it.key }, prefs.sections.map { it.key })
        assertTrue(prefs.sections.all { it.visible })
        assertEquals(emptyList<DashboardWidget>(), prefs.widgets)
        assertNull(prefs.accountIds)
    }

    @Test
    fun `incluye el panel de gadgets como seccion fija`() {
        assertTrue(defaultDashboardPrefs().sections.any { it.key == "widgetPanel" })
    }
}

class NormalizeDashboardPrefsTest {
    @Test
    fun `respeta orden y visibilidad, y anade al final las secciones que falten`() {
        val prefs = normalizeDashboardPrefs(
            json(
                """
                {"sections":[
                  {"key":"accounts","visible":true},
                  {"key":"quickActions","visible":false}
                ]}
                """,
            ),
        )
        assertEquals(DashboardSectionPref("accounts", true), prefs.sections[0])
        assertEquals(DashboardSectionPref("quickActions", false), prefs.sections[1])
        assertEquals(DASHBOARD_SECTIONS.size, prefs.sections.size)
        assertTrue(prefs.sections.last().visible)
    }

    @Test
    fun `acepta el formato v1 (solo sections) sin widgets ni accountIds`() {
        val prefs = normalizeDashboardPrefs(
            json("""{"sections":[{"key":"accounts","visible":false}]}"""),
        )
        assertEquals(emptyList<DashboardWidget>(), prefs.widgets)
        assertNull(prefs.accountIds)
    }

    @Test
    fun `descarta claves desconocidas, duplicados y basura`() {
        val prefs = normalizeDashboardPrefs(
            json(
                """
                {"sections":[
                  {"key":"hacker","visible":false},
                  {"key":"accounts","visible":false},
                  {"key":"accounts","visible":true}
                ]}
                """,
            ),
        )
        assertEquals(
            listOf(DashboardSectionPref("accounts", false)),
            prefs.sections.filter { it.key == "accounts" },
        )
        assertFalse(prefs.sections.any { it.key == "hacker" })
        assertEquals(defaultDashboardPrefs(), normalizeDashboardPrefs(null))
        assertEquals(defaultDashboardPrefs(), normalizeDashboardPrefs(JsonPrimitive("x")))
    }

    @Test
    fun `migra prefs v2 descartando claves widget sin perder los gadgets`() {
        val prefs = normalizeDashboardPrefs(
            json(
                """
                {"sections":[
                  {"key":"widget:w1","visible":false},
                  {"key":"accounts","visible":true}
                ],
                 "widgets":[{"id":"w1","type":"currencyTotals"}]}
                """,
            ),
        )
        assertFalse(prefs.sections.any { it.key.startsWith("widget:") })
        assertEquals(listOf("w1"), prefs.widgets.map { it.id })
    }

    @Test
    fun `normaliza gadgets con tamanio (default por tipo, invalido fuera)`() {
        val prefs = normalizeDashboardPrefs(
            json(
                """
                {"widgets":[
                  {"id":"w1","type":"accountCard","accountId":"a1","showMovements":true},
                  {"id":"w2","type":"currencyTotals","size":"lg"},
                  {"id":"w3","type":"ratePair","fromCurrencyId":"c1","toCurrencyId":"c2","size":"xxl"}
                ]}
                """,
            ),
        )
        assertEquals(
            DashboardWidget(
                id = "w1",
                type = WidgetType.ACCOUNT_CARD,
                size = WidgetSize.SM,
                accountId = "a1",
                showMovements = true,
                showDenominations = false,
            ),
            prefs.widgets[0],
        )
        assertEquals(WidgetSize.LG, prefs.widgets[1].size)
        assertEquals(WidgetSize.SM, prefs.widgets[2].size)
    }

    @Test
    fun `normaliza incomeCard con defaults, enums invalidos fuera y flags en false`() {
        val prefs = normalizeDashboardPrefs(
            json(
                """
                {"widgets":[
                  {"id":"w1","type":"incomeCard"},
                  {"id":"w2","type":"incomeCard","accountId":"a1","variant":"neon",
                   "metric":"net","defaultPeriod":"week","title":"Mi tarjeta",
                   "showTabs":false,"showNet":false}
                ]}
                """,
            ),
        )
        assertEquals(
            DashboardWidget(
                id = "w1",
                type = WidgetType.INCOME_CARD,
                size = WidgetSize.MD,
                accountId = null,
                variant = IncomeCardVariant.SOFT,
                metric = IncomeCardMetric.INCOME,
                defaultPeriod = IncomeCardPeriod.MONTH,
                title = null,
                showTabs = true,
                showDelta = true,
                showIncome = true,
                showExpense = true,
                showNet = true,
            ),
            prefs.widgets[0],
        )

        val w2 = prefs.widgets[1]
        assertEquals("a1", w2.accountId)
        // "neon" no existe → default.
        assertEquals(IncomeCardVariant.SOFT, w2.variant)
        assertEquals(IncomeCardMetric.NET, w2.metric)
        assertEquals(IncomeCardPeriod.WEEK, w2.defaultPeriod)
        assertEquals("Mi tarjeta", w2.title)
        assertEquals(false, w2.showTabs)
        assertEquals(false, w2.showNet)
        assertEquals(true, w2.showIncome)
    }

    @Test
    fun `descarta gadgets invalidos`() {
        val prefs = normalizeDashboardPrefs(
            json(
                """
                {"widgets":[
                  {"id":"sin-cuenta","type":"accountCard"},
                  {"id":"mismo-par","type":"ratePair","fromCurrencyId":"c1","toCurrencyId":"c1"},
                  {"id":"tipo-raro","type":"hacker"},
                  {"id":"ok","type":"ratePair","fromCurrencyId":"c1","toCurrencyId":"c2"}
                ]}
                """,
            ),
        )
        assertEquals(listOf("ok"), prefs.widgets.map { it.id })
    }

    @Test
    fun `descarta gadgets con id repetido y corta en MAX_WIDGETS`() {
        val repetidos = normalizeDashboardPrefs(
            json(
                """
                {"widgets":[
                  {"id":"w1","type":"currencyTotals"},
                  {"id":"w1","type":"ratePair","fromCurrencyId":"c1","toCurrencyId":"c2"}
                ]}
                """,
            ),
        )
        assertEquals(listOf("w1"), repetidos.widgets.map { it.id })
        assertEquals(WidgetType.CURRENCY_TOTALS, repetidos.widgets[0].type)

        val muchos = (1..MAX_WIDGETS + 3).joinToString(",") {
            """{"id":"w$it","type":"currencyTotals"}"""
        }
        val prefs = normalizeDashboardPrefs(json("""{"widgets":[$muchos]}"""))
        assertEquals(MAX_WIDGETS, prefs.widgets.size)
    }

    @Test
    fun `normaliza accountIds con dedupe y null para todas`() {
        assertEquals(
            listOf("a", "b"),
            normalizeDashboardPrefs(json("""{"accountIds":["a","b","a"]}""")).accountIds,
        )
        assertNull(normalizeDashboardPrefs(json("{}")).accountIds)
        assertEquals(
            emptyList<String>(),
            normalizeDashboardPrefs(json("""{"accountIds":[]}""")).accountIds,
        )
        // Basura dentro del array: se descarta sin tumbar el resto.
        assertEquals(
            listOf("a"),
            normalizeDashboardPrefs(json("""{"accountIds":["a",7,null,""]}""")).accountIds,
        )
    }
}

class ParseSerializeDashboardPrefsTest {
    @Test
    fun `hace roundtrip`() {
        val prefs = normalizeDashboardPrefs(
            json(
                """
                {"sections":[{"key":"accounts","visible":false}],
                 "widgets":[
                   {"id":"w1","type":"currencyTotals"},
                   {"id":"w2","type":"incomeCard","variant":"dark","metric":"expense"}
                 ],
                 "accountIds":["a1"]}
                """,
            ),
        )
        assertEquals(prefs, parseDashboardPrefs(serializeDashboardPrefs(prefs)))
    }

    @Test
    fun `null o JSON corrupto devuelven los valores por defecto`() {
        assertEquals(defaultDashboardPrefs(), parseDashboardPrefs(null))
        assertEquals(defaultDashboardPrefs(), parseDashboardPrefs(""))
        assertEquals(defaultDashboardPrefs(), parseDashboardPrefs("{rota"))
        assertEquals(defaultDashboardPrefs(), parseDashboardPrefs("null"))
    }
}
