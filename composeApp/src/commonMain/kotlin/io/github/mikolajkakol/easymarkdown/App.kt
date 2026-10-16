package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

data class OpenedDocument(val title: String, val content: String)

@Composable
fun App(
    document: OpenedDocument,
    onOpenFile: (() -> Unit)?,
    onShowSample: (() -> Unit)?,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        var openedDiagram by rememberSaveable(document) { mutableStateOf<String?>(null) }
        // The diagram screen is drawn over the document so its scroll position and rendered diagrams are kept.
        Box(Modifier.fillMaxSize()) {
            DocumentScreen(document, darkTheme, onOpenFile, onShowSample, onOpenDiagram = { openedDiagram = it })
            openedDiagram?.let { code ->
                DiagramScreen(code = code, darkTheme = darkTheme, onClose = { openedDiagram = null })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentScreen(
    document: OpenedDocument,
    darkTheme: Boolean,
    onOpenFile: (() -> Unit)?,
    onShowSample: (() -> Unit)?,
    onOpenDiagram: (String) -> Unit,
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
        val typography = with(MaterialTheme.typography) {
            markdownTypography(
                h1 = headlineLarge,
                h2 = headlineMedium,
                h3 = headlineSmall,
                h4 = titleLarge,
                h5 = titleMedium,
                h6 = titleSmall,
            )
        }
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
                        typography = typography,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is DocumentSegment.Mermaid -> Box(Modifier.fillMaxWidth()) {
                        LocalMermaidRenderer.current.Render(
                            code = segment.code,
                            darkTheme = darkTheme,
                            zoomable = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Covers the diagram so taps open the full screen view instead of reaching the WebView.
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(onClickLabel = "Open diagram") { onOpenDiagram(segment.code) },
                        )
                    }
                }
            }
        }
    }
}
