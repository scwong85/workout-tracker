package com.workouttracker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.workouttracker.domain.Bucket
import com.workouttracker.domain.Category
import com.workouttracker.domain.TrendPoint

/** Vertical bars with a label under each; labels thin out when there are many bars. */
@Composable
fun BarChart(buckets: List<Bucket>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val max = (buckets.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val every = when {
        buckets.size <= 12 -> 1
        buckets.size <= 31 -> 5
        else -> buckets.size / 6
    }
    Column(
        modifier.semantics {
            contentDescription = "Workouts chart: " + buckets.filter { it.value > 0 }.joinToString { "${it.label} ${it.value}" }
        },
    ) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            if (buckets.isEmpty()) return@Canvas
            val slot = size.width / buckets.size
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            buckets.forEachIndexed { i, b ->
                val x = i * slot + (slot - barWidth) / 2
                val radius = CornerRadius(barWidth / 3)
                drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), radius)
                if (b.value > 0) {
                    val h = size.height * b.value / max
                    drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), radius)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            buckets.forEachIndexed { i, b ->
                Text(
                    if (i % every == 0 || i == buckets.lastIndex) b.label else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Simple line with dots; [format] renders the min/max axis labels. */
@Composable
fun LineChart(points: List<TrendPoint>, format: (Double) -> String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (points.isEmpty()) return
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.semantics { contentDescription = "Trend from ${format(points.first().value)} to ${format(points.last().value)}" }) {
        Column(Modifier.height(160.dp).padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(format(max), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(format(min), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.weight(1f).height(160.dp)) {
            val pad = 8.dp.toPx()
            val h = size.height - 2 * pad
            val w = size.width - 2 * pad
            val span = (max - min).takeIf { it > 0 } ?: 1.0
            fun pos(i: Int, v: Double): Offset {
                val x = if (points.size == 1) size.width / 2 else pad + w * i / (points.size - 1)
                val y = if (max == min) size.height / 2 else pad + h * (1 - ((v - min) / span)).toFloat()
                return Offset(x, y)
            }
            drawLine(grid, Offset(0f, pad), Offset(size.width, pad))
            drawLine(grid, Offset(0f, size.height - pad), Offset(size.width, size.height - pad))
            val path = Path()
            points.forEachIndexed { i, p ->
                val o = pos(i, p.value)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, p -> drawCircle(color, radius = 4.dp.toPx(), center = pos(i, p.value)) }
        }
    }
}

/** Horizontal share bars for the muscle-group split. */
@Composable
fun CategorySplit(split: List<Pair<Category, Int>>, modifier: Modifier = Modifier) {
    val total = split.sumOf { it.second }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        split.forEach { (cat, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(cat, size = 28.dp)
                Text(cat.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp).padding(start = 8.dp))
                Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxWidth(count.toFloat() / total).fillMaxHeight().background(cat.color, RoundedCornerShape(5.dp)))
                }
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}
