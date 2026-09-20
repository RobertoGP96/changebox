package com.lolo.changebox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDownLeft
import com.composables.icons.lucide.ArrowRightLeft
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal
import com.lolo.changebox.data.repo.TxRowUi
import com.lolo.changebox.domain.fmtSignedMinor
import com.lolo.changebox.ui.theme.ChangeboxColors

// Filas del historial de movimientos (port de components/tx-list.tsx): icono
// por tipo con tinte según signo, título/subtítulo y monto con signo.

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "INCOME" -> Lucide.ArrowDownLeft
    "EXPENSE" -> Lucide.ArrowUpRight
    "TRANSFER" -> Lucide.ArrowRightLeft
    "ADJUSTMENT" -> Lucide.SlidersHorizontal
    else -> Lucide.ArrowRightLeft
}

@Composable
fun TxRowItem(row: TxRowUi, onClick: () -> Unit) {
    val positive = row.amountMinor > 0
    val ext = ChangeboxColors.extended
    ChangeboxCard(corner = 16, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (positive) ext.ok.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    kindIcon(row.kind),
                    contentDescription = null,
                    tint = if (positive) ext.ok else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (row.subtitle.isNotEmpty()) append("${row.subtitle} · ")
                        append(fmtDate(row.occurredAt))
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                fmtSignedMinor(row.amountMinor, row.currency),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (positive) ext.ok else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TxList(rows: List<TxRowUi>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            TxRowItem(row) { onOpen(row.id) }
        }
    }
}

