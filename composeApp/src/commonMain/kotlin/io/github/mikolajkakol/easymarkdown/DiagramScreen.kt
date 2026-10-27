package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Full screen view of a single diagram with pinch-to-zoom. */
@Composable
fun DiagramScreen(code: String, darkTheme: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    DetailScreen(title = "Diagram", copyText = { code }, onClose = onClose, modifier = modifier) { padding ->
        LocalMermaidRenderer.current.Render(
            code = code,
            darkTheme = darkTheme,
            zoomable = true,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

/** Full screen view of a table, scrollable in both directions, with wider columns than inline. */
@Composable
fun TableScreen(table: DocumentSegment.Table, onClose: () -> Unit, modifier: Modifier = Modifier) {
    DetailScreen(title = "Table", copyText = { tableAsText(table) }, onClose = onClose, modifier = modifier) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SelectionContainer { MarkdownTable(table, maxColumnWidth = 360.dp) }
        }
    }
}

/** Full screen view of a code block, scrollable in both directions. */
@Composable
fun CodeScreen(code: DocumentSegment.Code, onClose: () -> Unit, modifier: Modifier = Modifier) {
    DetailScreen(
        title = code.language.ifEmpty { "Code" },
        copyText = { code.code },
        onClose = onClose,
        modifier = modifier,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SelectionContainer { CodeBlock(code) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    title: String,
    copyText: () -> String,
    onClose: () -> Unit,
    modifier: Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    CopyButton(copyText, "Copy")
                    TextButton(onClick = onClose) { Text("Close") }
                },
            )
        },
        content = content,
    )
}
