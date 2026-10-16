package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown

data class OpenedDocument(val title: String, val content: String)

@Composable
fun App(
    document: OpenedDocument,
    onOpenFile: (() -> Unit)?,
    onShowSample: (() -> Unit)?,
) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        DocumentScreen(document, darkTheme, onOpenFile, onShowSample)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentScreen(
    document: OpenedDocument,
    darkTheme: Boolean,
    onOpenFile: (() -> Unit)?,
    onShowSample: (() -> Unit)?,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (onShowSample != null) TextButton(onClick = onShowSample) { Text("Sample") }
                    if (onOpenFile != null) TextButton(onClick = onOpenFile) { Text("Open") }
                },
            )
        },
    ) { padding ->
        val segments = remember(document.content) { splitDocument(document.content) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            segments.forEach { segment ->
                when (segment) {
                    is DocumentSegment.Markdown -> Markdown(
                        content = segment.text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is DocumentSegment.Mermaid -> MermaidDiagram(
                        code = segment.code,
                        darkTheme = darkTheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
