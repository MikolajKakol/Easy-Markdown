package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Small button copying [text] to the clipboard; shows a check mark for a moment after copying. */
@Composable
fun CopyButton(text: () -> String, contentDescription: String, modifier: Modifier = Modifier) {
    @Suppress("DEPRECATION") // LocalClipboard needs a platform specific ClipEntry; plain text is all we copy.
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text()))
            copied = true
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (copied) CheckIcon else CopyIcon,
            contentDescription = if (copied) "Copied" else contentDescription,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Table as tab separated values with formatting stripped, so it pastes into spreadsheets as cells. */
fun tableAsText(table: DocumentSegment.Table): String =
    (listOf(table.header) + table.rows).joinToString("\n") { row ->
        row.joinToString("\t") { cell -> inlineMarkdown(cell).text.replace('\n', ' ') }
    }

// Material Symbols "content_copy" and "check".
private val CopyIcon: ImageVector = materialIcon("Copy") {
    moveTo(16f, 1f)
    horizontalLineTo(4f)
    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
    verticalLineToRelative(14f)
    horizontalLineToRelative(2f)
    verticalLineTo(3f)
    horizontalLineToRelative(12f)
    verticalLineTo(1f)
    close()
    moveTo(19f, 5f)
    horizontalLineTo(8f)
    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
    verticalLineToRelative(14f)
    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
    horizontalLineToRelative(11f)
    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
    verticalLineTo(7f)
    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
    close()
    moveTo(19f, 21f)
    horizontalLineTo(8f)
    verticalLineTo(7f)
    horizontalLineToRelative(11f)
    verticalLineToRelative(14f)
    close()
}

private val CheckIcon: ImageVector = materialIcon("Check") {
    moveTo(9f, 16.17f)
    lineTo(4.83f, 12f)
    lineToRelative(-1.42f, 1.41f)
    lineTo(9f, 19f)
    lineTo(21f, 7f)
    lineToRelative(-1.41f, -1.41f)
    close()
}

private fun materialIcon(name: String, pathBuilder: PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply { path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder) }
        .build()
