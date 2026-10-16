package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MinColumnWidth = 48.dp
private val TableShape = RoundedCornerShape(8.dp)

/**
 * Draws a markdown table. Each column is as wide as its widest cell, but at most [maxColumnWidth];
 * longer cell text wraps. The table is as wide as its columns, so wrap it in a horizontal scroll.
 */
@Composable
fun MarkdownTable(table: DocumentSegment.Table, maxColumnWidth: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyMedium
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant)
    val linkStyles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
    val lineColor = colors.outlineVariant
    val rows = remember(table) { listOf(table.header) + table.rows }
    val columns = table.header.size

    Layout(
        modifier = modifier.clip(TableShape).border(1.dp, lineColor, TableShape),
        content = {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, cell ->
                    Text(
                        text = remember(cell, colors) { inlineMarkdown(cell, codeStyle, linkStyles) },
                        style = textStyle,
                        fontWeight = if (r == 0) FontWeight.Bold else null,
                        textAlign = when (table.alignments[c]) {
                            ColumnAlignment.Start -> TextAlign.Start
                            ColumnAlignment.Center -> TextAlign.Center
                            ColumnAlignment.End -> TextAlign.End
                        },
                        modifier = Modifier
                            .background(if (r == 0) colors.surfaceVariant else Color.Transparent)
                            .drawBehind {
                                val stroke = 1.dp.toPx()
                                if (r < rows.lastIndex) {
                                    drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), stroke)
                                }
                                if (c < columns - 1) {
                                    drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        },
    ) { measurables, _ ->
        val minWidth = MinColumnWidth.roundToPx()
        val maxWidth = maxColumnWidth.roundToPx()
        val widths = IntArray(columns) { c ->
            rows.indices.maxOf { r -> measurables[r * columns + c].maxIntrinsicWidth(Constraints.Infinity) }
                .coerceIn(minWidth, maxWidth)
        }
        val heights = IntArray(rows.size) { r ->
            (0 until columns).maxOf { c -> measurables[r * columns + c].minIntrinsicHeight(widths[c]) }
        }
        val placeables = measurables.mapIndexed { i, measurable ->
            measurable.measure(Constraints.fixed(widths[i % columns], heights[i / columns]))
        }
        layout(widths.sum(), heights.sum()) {
            var y = 0
            for (r in rows.indices) {
                var x = 0
                for (c in 0 until columns) {
                    placeables[r * columns + c].place(x, y)
                    x += widths[c]
                }
                y += heights[r]
            }
        }
    }
}
