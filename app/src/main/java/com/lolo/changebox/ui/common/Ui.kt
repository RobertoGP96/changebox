package com.lolo.changebox.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.ui.theme.Brand
import com.lolo.changebox.ui.theme.BrandLight
import com.lolo.changebox.ui.theme.BrandMid
import com.lolo.changebox.ui.theme.ChangeboxColors
import com.lolo.changebox.ui.theme.Gold
import com.lolo.changebox.ui.theme.Navy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Componentes base portados del look de la web (tarjetas 18dp, header con
// gradiente petróleo, badges tintados). El "toast" de la web se sirve vía
// LocalToast (snackbar del Scaffold raíz).

val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Ancho de contenido responsivo: en el móvil llena la pantalla y en
 * tablets/landscape se limita y centra (equivalente a los md:max-w de la web).
 * Úsalo en la columna de contenido bajo el ScreenHeader (que sí va a sangre).
 */
fun Modifier.contentWidth(): Modifier = this.widthIn(max = 560.dp).fillMaxWidth()

val HeaderGradient = Brush.linearGradient(
    colors = listOf(Navy, Brand, BrandMid),
)

val CtaGradient = Brush.linearGradient(colors = listOf(Brand, BrandLight))

val ProgressGradient = Brush.horizontalGradient(colors = listOf(Brand, BrandLight))

val GoldGradient = Brush.linearGradient(colors = listOf(Gold, Color(0xFFEEC06B)))

private val ES = Locale.forLanguageTag("es")

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", ES)
val DATE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy, HH:mm", ES)
val SHORT_DATE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm", ES)

fun fmtDate(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        .toLocalDate().format(DATE_FMT)

fun fmtDateTime(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime().format(DATE_TIME_FMT)

fun fmtShortDateTime(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime().format(SHORT_DATE_TIME_FMT)

/** Cabecera de pantalla con el gradiente de la marca y acentos decorativos. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(HeaderGradient),
    ) {
        // Acentos decorativos de la identidad Changebox
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-48).dp)
                .size(160.dp)
                .blur(48.dp)
                .background(BrandLight.copy(alpha = 0.25f), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-80).dp, y = 64.dp)
                .size(128.dp)
                .blur(40.dp)
                .background(Gold.copy(alpha = 0.15f), CircleShape)
        )

        // El gradiente se extiende bajo la barra de estado (edge-to-edge);
        // el contenido baja para no chocar con el reloj/iconos del sistema.
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Lucide.ArrowLeft,
                            contentDescription = "Volver",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                }
                Text(
                    title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (actions != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        content = actions,
                    )
                }
            }
            content?.invoke(this)
        }
    }
}

/** Tarjeta blanca con borde fino, la superficie estándar de la web. */
@Composable
fun ChangeboxCard(
    modifier: Modifier = Modifier,
    corner: Int = 18,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
        ) { Column(content = content) }
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandMid,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

enum class BadgeVariant { NEUTRAL, OK, WARN, DANGER, FEATURED }

@Composable
fun ChangeboxBadge(
    text: String,
    variant: BadgeVariant = BadgeVariant.NEUTRAL,
    icon: ImageVector? = null,
) {
    val ext = ChangeboxColors.extended
    val (bg, fg) = when (variant) {
        BadgeVariant.NEUTRAL -> ext.chip to MaterialTheme.colorScheme.onSurfaceVariant
        BadgeVariant.OK -> ext.ok.copy(alpha = 0.14f) to ext.ok
        BadgeVariant.WARN -> ext.warn.copy(alpha = 0.15f) to ext.warn
        BadgeVariant.DANGER -> MaterialTheme.colorScheme.error.copy(alpha = 0.13f) to
            MaterialTheme.colorScheme.error
        BadgeVariant.FEATURED -> ext.gold.copy(alpha = 0.18f) to ext.gold
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        }
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Chip de icono cuadrado (fondo chip + tinta brand) usado en listas. */
@Composable
fun IconChip(icon: ImageVector, size: Int = 44, corner: Int = 14, contentDescription: String? = null) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(ChangeboxColors.extended.chip),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size((size * 5 / 11).dp),
        )
    }
}

@Composable
fun ErrorBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
) {
    ChangeboxCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconChip(icon, size = 48)
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (ctaLabel != null && onCta != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                PrimaryButton(text = ctaLabel, onClick = onCta)
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 18.dp,
            vertical = if (large) 14.dp else 10.dp,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = if (large) 14.sp else 13.sp)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = Color.White,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Campo etiquetado (label pequeña en negrita, como los forms de la web). */
@Composable
fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
        if (hint != null) {
            Text(hint, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChangeboxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    decimal: Boolean = false,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = placeholder?.let { { Text(it, fontSize = 13.sp) } },
        singleLine = singleLine,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = when {
            decimal -> androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            )
            numeric -> androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
            else -> androidx.compose.foundation.text.KeyboardOptions.Default
        },
    )
}

/** Select estilo web sobre ExposedDropdownMenu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChangeboxSelect(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    display: (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = "Elige una opción",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.let(display) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(13.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier
                .menuAnchor(
                    androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                    enabled,
                )
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option), fontSize = 13.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Botonera de opciones tipo tabs de la web (Gasto/Ingreso/Transferencia…). */
@Composable
fun SegmentedTabs(
    options: List<Pair<String, String>>, // key to label
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (selected) ChangeboxColors.extended.chip
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(13.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Selector de fecha: campo de solo lectura que abre el DatePicker M3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    maxToday: Boolean = false,
    minDate: LocalDate? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value.format(DATE_FMT),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            shape = RoundedCornerShape(13.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(13.dp))
                .clickable { open = true }
        )
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // El DatePicker trabaja en UTC a medianoche.
                        var picked = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        if (maxToday && picked.isAfter(LocalDate.now())) picked = LocalDate.now()
                        if (minDate != null && picked.isBefore(minDate)) picked = minDate
                        onChange(picked)
                    }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/** Barra de progreso con el gradiente de la marca (top de categorías). */
@Composable
fun GradientBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(ProgressGradient)
        )
    }
}

/** Confirmación inline (patrón de la web): texto + Confirmar/Cancelar. */
@Composable
fun InlineConfirm(
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DangerButton(confirmLabel, onClick = onConfirm, enabled = !busy)
            GhostButton("Cancelar", onClick = onCancel, enabled = !busy)
        }
    }
}

/** Fila etiqueta→valor del detalle de movimiento. */
@Composable
fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Box(Modifier.weight(0.6f), contentAlignment = Alignment.CenterEnd) {
            value()
        }
    }
}

@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        badge?.invoke()
    }
}

