package com.timmat.financetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.timmat.financetracker.ui.dashboard.MonthBar
import com.timmat.financetracker.ui.theme.Expense
import com.timmat.financetracker.ui.theme.Income

/**
 * Grouped bar chart: for each month show three bars — income, expense, remaining.
 * Hand-drawn in Canvas (no external chart deps).
 */
@Composable
fun MonthlyChart(
    bars: List<MonthBar>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return
    val incomeColor = Income
    val expenseColor = Expense
    val remainingColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    val maxValue = bars.flatMap { listOf(it.income, it.expense, kotlin.math.abs(it.remaining)) }
        .maxOrNull()?.takeIf { it > 0 } ?: 1.0

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 4.dp),
        ) {
            val groupWidth = size.width / bars.size
            val barWidth = groupWidth / 4.5f
            val gap = barWidth * 0.25f
            val chartH = size.height - 20f // reserve space for month labels
            bars.forEachIndexed { index, bar ->
                val x0 = index * groupWidth + (groupWidth - (barWidth * 3 + gap * 2)) / 2
                drawBar(incomeColor, x0, bar.income, maxValue, chartH, barWidth)
                drawBar(expenseColor, x0 + barWidth + gap, bar.expense, maxValue, chartH, barWidth)
                drawBar(remainingColor, x0 + (barWidth + gap) * 2, kotlin.math.abs(bar.remaining), maxValue, chartH, barWidth)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(incomeColor, "Income")
            LegendDot(expenseColor, "Expenses")
            LegendDot(remainingColor, "Remaining")
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    color: Color,
    x: Float,
    value: Double,
    maxValue: Double,
    chartH: Float,
    barWidth: Float,
) {
    val h = (value / maxValue).toFloat() * chartH
    drawRect(
        color = color,
        topLeft = Offset(x, chartH - h),
        size = Size(barWidth, h.coerceAtLeast(1f)),
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color,
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.size(10.dp),
        ) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
