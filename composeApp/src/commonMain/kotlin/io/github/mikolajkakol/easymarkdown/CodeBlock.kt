package io.github.mikolajkakol.easymarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** Code drawn without wrapping in a monospace font, so wrap it in a horizontal scroll. */
@Composable
fun CodeBlock(code: DocumentSegment.Code, modifier: Modifier = Modifier) {
    val text = remember(code.code) { expandTabs(code.code) }
    Text(
        text = text,
        softWrap = false,
        style = TextStyle(
            fontFamily = codeFontFamily(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier,
    )
}

/** Replaces tabs with spaces up to the next multiple of [tabSize] columns. */
fun expandTabs(text: String, tabSize: Int = 4): String = text.lines().joinToString("\n") { line ->
    if ('\t' !in line) return@joinToString line
    buildString {
        for (c in line) {
            if (c == '\t') repeat(tabSize - length % tabSize) { append(' ') } else append(c)
        }
    }
}
