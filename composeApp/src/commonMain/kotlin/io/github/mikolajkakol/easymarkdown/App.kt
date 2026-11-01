package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

data class OpenedDocument(val title: String, val content: String)

@Composable
fun App(
    document: OpenedDocument,
    onOpenFile: (() -> Unit)?,
    darkTheme: Boolean = isSystemInDarkTheme(),
    search: SearchState = rememberSearchState(),
) {
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        val segments = remember(document.content) { splitDocument(document.content) }
        // Index of the diagram or table shown full screen; kept when the same document is reloaded after a change.
        var expandedIndex by rememberSaveable(document.title) { mutableStateOf<Int?>(null) }
        // Full screen views are drawn over the document so its scroll position and rendered diagrams are kept.
        // Finding is done in the document, so a full screen view is closed when the find bar is opened.
        LaunchedEffect(search.focusRequests) { if (search.visible) expandedIndex = null }
        Box(Modifier.fillMaxSize()) {
            DocumentScreen(document, segments, darkTheme, onOpenFile, search, onExpand = { expandedIndex = it })
            val close = { expandedIndex = null }
            when (val expanded = expandedIndex?.let(segments::getOrNull)) {
                is DocumentSegment.Mermaid -> DiagramScreen(expanded.code, darkTheme, onClose = close)
                is DocumentSegment.Table -> TableScreen(expanded, onClose = close)
                is DocumentSegment.Code -> CodeScreen(expanded, onClose = close)
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentScreen(
    document: OpenedDocument,
    segments: List<DocumentSegment>,
    darkTheme: Boolean,
    onOpenFile: (() -> Unit)?,
    search: SearchState,
    onExpand: (index: Int) -> Unit,
) {
    val documentSearch = remember(segments) { DocumentSearch(segments) }
    val matches = remember(documentSearch, search.query, search.visible) {
        if (search.visible) documentSearch.find(search.query) else emptyList()
    }
    val current = matches.getOrNull(search.current.coerceAtMost(matches.lastIndex))
    SideEffect { search.matchCount = matches.size }
    val matchesBySegment = remember(matches) { matches.groupBy { it.segment } }
    val scrollState = rememberScrollState()
    // Top and height of each segment in the scrolled column, for scrolling to a match.
    val segmentBounds = remember(segments) { HashMap<Int, Pair<Float, Int>>() }
    LaunchedEffect(current) {
        val match = current ?: return@LaunchedEffect
        val (top, height) = segmentBounds[match.segment] ?: return@LaunchedEffect
        val target = top + match.position * height - scrollState.viewportSize / 3f
        scrollState.animateScrollTo(target.toInt().coerceIn(0, scrollState.maxValue))
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        TextButton(onClick = search::open) { Text("Find") }
                        if (onOpenFile != null) TextButton(onClick = onOpenFile) { Text("Open") }
                    },
                )
                if (search.visible) FindBar(search)
            }
        },
    ) { padding ->
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            segments.forEachIndexed { index, segment ->
                val segmentMatches = matchesBySegment[index].orEmpty()
                val placed = Modifier.onPlaced { segmentBounds[index] = it.positionInParent().y to it.size.height }
                when (segment) {
                    is DocumentSegment.Markdown -> Markdown(
                        content = segment.text,
                        typography = typography,
                        annotator = remember(segmentMatches, current) {
                            searchAnnotator(segmentMatches.map { Highlight(it.range, it == current) })
                        },
                        modifier = placed.fillMaxWidth(),
                    )
                    is DocumentSegment.Mermaid -> Box(
                        placed
                            .fillMaxWidth()
                            .then(
                                // The diagram is an image, so one whose code matches gets a frame.
                                if (segmentMatches.isEmpty()) Modifier else Modifier.border(
                                    width = if (current in segmentMatches) 3.dp else 2.dp,
                                    color = highlightStyle(current in segmentMatches).background,
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            ),
                    ) {
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
                                .semantics { contentDescription = "Mermaid diagram" }
                                .clickable(onClickLabel = "Open diagram") { onExpand(index) },
                        )
                    }
                    is DocumentSegment.Code -> Box(
                        placed
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .semantics { contentDescription = "Code block" }
                            .clickable(onClickLabel = "Open code") { onExpand(index) },
                    ) {
                        CodeBlock(
                            segment,
                            Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                            highlights = segmentMatches.map { Highlight(it.range, it == current) },
                        )
                    }
                    is DocumentSegment.Table -> Box(placed.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        MarkdownTable(
                            table = segment,
                            maxColumnWidth = 260.dp,
                            highlights = segmentMatches.groupBy { it.cell!! }.mapValues { (_, hits) -> current in hits },
                            modifier = Modifier
                                .semantics { contentDescription = "Markdown table" }
                                .clickable(onClickLabel = "Open table") { onExpand(index) },
                        )
                    }
                }
            }
        }
    }
}
