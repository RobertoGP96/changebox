package com.lolo.changebox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.WandSparkles
import com.lolo.changebox.domain.CountableDenomination
import com.lolo.changebox.domain.DenominationKind
import com.lolo.changebox.domain.DisplayCurrency
import com.lolo.changebox.domain.SuggestibleDenomination
import com.lolo.changebox.domain.clampQty
import com.lolo.changebox.domain.countedTotalMinor
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.suggestDistribution
import com.lolo.changebox.ui.theme.ChangeboxColors

// Filas de conteo por denominación compartidas por el arqueo, la calculadora
// y el desglose de movimientos (ports de denomination-counter.tsx y
// denomination-breakdown-field.tsx). El subtotal va en línea propia bajo la
// fila para no desbordar en pantallas estrechas.

data class CounterDenomination(
    val id: String,
    val valueMinor: Long,
    val kind: String,
    /** Texto extra junto al tipo (ej. "quedan 4" en Changeboxs con stock). */
    val hint: String? = null,
    /** Stock derivado (solo informativo/sugerencias en salidas). */
    val available: Int? = null,
)

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, label: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun DenominationCounter(
    denominations: List<CounterDenomination>,
    quantities: Map<String, Int>,
    onQtyChange: (String, Int) -> Unit,
    currency: DisplayCurrency,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        denominations.forEach { d ->
            val qty = quantities[d.id] ?: 0
            val subtotal = d.valueMinor * qty
            val valueLabel = fmtMinor(d.valueMinor, currency)
            ChangeboxCard(corner = 16) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                valueLabel,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                buildString {
                                    append(
                                        runCatching { DenominationKind.valueOf(d.kind).labelEs }
                                            .getOrDefault(d.kind)
                                    )
                                    d.hint?.let { append(" · $it") }
                                },
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        StepButton(Lucide.Minus, { onQtyChange(d.id, clampQty(qty - 1)) }, "Quitar $valueLabel")
                        QtyInput(
                            qty = qty,
                            onChange = { onQtyChange(d.id, clampQty(it)) },
                        )
                        StepButton(Lucide.Plus, { onQtyChange(d.id, clampQty(qty + 1)) }, "Añadir $valueLabel")
                    }
                    if (qty > 0) {
                        HorizontalDivider(
                            Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                "$qty × $valueLabel",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                fmtMinor(subtotal, currency),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QtyInput(qty: Int, onChange: (Int) -> Unit) {
    // El texto local permite vaciar el campo mientras se edita.
    var text by remember(qty) { mutableStateOf(if (qty == 0) "" else qty.toString()) }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val clean = raw.filter { it.isDigit() }.take(7)
            text = clean
            onChange(clean.toIntOrNull() ?: 0)
        },
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 9.dp),
        textStyle = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) {
                    Text(
                        "0",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * Desglose de denominaciones de un movimiento sobre una caja CASH_BOX. Con el
 * monto ya escrito, «Sugerir» rellena una distribución exacta (mayor-primero
 * con backtracking); en salidas respeta el stock derivado.
 */
@Composable
fun DenominationBreakdownField(
    title: String,
    denominations: List<CounterDenomination>,
    currency: DisplayCurrency,
    targetMinor: Long?,
    quantities: Map<String, Int>,
    onQtyChange: (Map<String, Int>) -> Unit,
    outflow: Boolean,
) {
    var suggestError by remember { mutableStateOf<String?>(null) }

    val countable = denominations.map { CountableDenomination(it.id, it.valueMinor) }
    val totalMinor = countedTotalMinor(countable, quantities)
    val matches = targetMinor != null && totalMinor == targetMinor
    val started = quantities.values.any { it > 0 }

    val rows = denominations.map { d ->
        d.copy(
            hint = if (outflow && d.available != null) {
                if (d.available > 0) "quedan ${d.available}" else "sin stock"
            } else {
                null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = targetMinor != null && targetMinor > 0) {
                        if (targetMinor == null || targetMinor <= 0) return@clickable
                        suggestError = null
                        val pool = denominations.map {
                            SuggestibleDenomination(
                                id = it.id,
                                valueMinor = it.valueMinor,
                                available = if (outflow) maxOf(0, it.available ?: 0) else null,
                            )
                        }
                        val suggestion = suggestDistribution(pool, targetMinor)
                        if (suggestion == null) {
                            suggestError = if (outflow) {
                                "No hay combinación exacta con las denominaciones disponibles en la caja."
                            } else {
                                "No hay combinación exacta con las denominaciones de la moneda."
                            }
                        } else {
                            onQtyChange(suggestion)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Lucide.WandSparkles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    "Sugerir distribución",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DenominationCounter(
            denominations = rows,
            quantities = quantities,
            onQtyChange = { id, qty ->
                suggestError = null
                onQtyChange(quantities + (id to qty))
            },
            currency = currency,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    append("Desglose: ${fmtMinor(totalMinor, currency)}")
                    if (targetMinor != null && !matches) {
                        append(" · monto: ${fmtMinor(targetMinor, currency)}")
                    }
                },
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            when {
                targetMinor == null -> ChangeboxBadge("Escribe el monto", BadgeVariant.NEUTRAL)
                matches -> ChangeboxBadge("Cuadra", BadgeVariant.OK)
                totalMinor > targetMinor -> ChangeboxBadge(
                    "Sobra ${fmtMinor(totalMinor - targetMinor, currency)}",
                    if (started) BadgeVariant.DANGER else BadgeVariant.WARN,
                )
                else -> ChangeboxBadge(
                    "Falta ${fmtMinor(targetMinor - totalMinor, currency)}",
                    if (started) BadgeVariant.DANGER else BadgeVariant.WARN,
                )
            }
        }

        suggestError?.let { ErrorBox(it) }
    }
}

