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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/**
 * A shown document. [location] identifies the file it was read from, so documents opened from links
 * keep their own scroll position; null for documents that are not files.
 */
data class OpenedDocument(val title: String, val content: String, val location: String? = null)

/**
 * Shows [document].
 *
 * Links to other local files go to [onOpenLink], which returns false to open the link with the system instead,
 * e.g. for a file that is not markdown. Web links always open with the system.
 * When [onBack] is set, the top bar shows a back button, also bound to the back gesture and Escape key,
 * returning to the document the shown one was opened from.
 */
@Composable
fun App(
    document: OpenedDocument,
    onOpenFile: (() -> Unit)?,
    darkTheme: Boolean = isSystemInDarkTheme(),
    search: SearchState = rememberSearchState(),
    onOpenLink: ((LocalLink) -> Boolean)? = null,
    onBack: (() -> Unit)? = null,
) {
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        PlatformBackHandler(enabled = onBack != null) { onBack?.invoke() }
        // Each document of the back stack keeps its scroll position and full screen view.
        val documentStates = rememberSaveableStateHolder()
        CompositionLocalProvider(LocalUriHandler provides rememberLinkHandler(onOpenLink)) {
            documentStates.SaveableStateProvider(document.location ?: document.title) {
                DocumentWithFullScreenViews(document, onOpenFile, darkTheme, search, onBack)
            }
        }
    }
}

/** Opens links to local files with [onOpenLink] and other links with the platform's handler. */
@Composable
private fun rememberLinkHandler(onOpenLink: ((LocalLink) -> Boolean)?): UriHandler {
    val platformHandler = LocalUriHandler.current
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    return remember(platformHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                // Anchors within the document are not supported, and the system cannot open them either.
                if (uri.trim().startsWith("#")) return
                val local = parseLocalLink(uri)
                if (local != null && currentOnOpenLink?.invoke(local) == true) return
                runCatching { platformHandler.openUri(uri) }
                    .onFailure { println("Cannot open link $uri: ${it.message}") }
            }
        }
    }
}

@Composable
private fun DocumentWithFullScreenViews(
    document: OpenedDocument,
    onOpenFile: (() -> Unit)?,
    darkTheme: Boolean,
    search: SearchState,
    onBack: (() -> Unit)?,
) {
    val segments = remember(document.content) { splitDocument(document.content) }
    // Index of the diagram or table shown full screen; kept when the same document is reloaded after a change.
    var expandedIndex by rememberSaveable(document.title) { mutableStateOf<Int?>(null) }
    // Full screen views are drawn over the document so its scroll position and rendered diagrams are kept.
    // Finding is done in the document, so a full screen view is closed when the find bar is opened.
    LaunchedEffect(search.focusRequests) { if (search.visible) expandedIndex = null }
    Box(Modifier.fillMaxSize()) {
        DocumentScreen(document, segments, darkTheme, onOpenFile, search, onBack, onExpand = { expandedIndex = it })
        val close = { expandedIndex = null }
        when (val expanded = expandedIndex?.let(segments::getOrNull)) {
            is DocumentSegment.Mermaid -> DiagramScreen(expanded.code, darkTheme, onClose = close)
            is DocumentSegment.Table -> TableScreen(expanded, onClose = close)
            is DocumentSegment.Code -> CodeScreen(expanded, onClose = close)
            else -> Unit
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
    onBack: (() -> Unit)?,
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
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(BackArrow, contentDescription = "Back") }
                    },
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
        // The whole document is one selection, so text can be selected across paragraphs, tables and code.
        SelectionContainer(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                            CopyButton({ segment.code }, "Copy diagram source", Modifier.align(Alignment.TopEnd).padding(4.dp))
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
                            CopyButton({ segment.code }, "Copy code", Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                        // The copy button sits beside the table rather than over its header cells.
                        is DocumentSegment.Table -> Row(placed) {
                            Box(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())) {
                                MarkdownTable(
                                    table = segment,
                                    maxColumnWidth = 260.dp,
                                    highlights = segmentMatches.groupBy { it.cell!! }.mapValues { (_, hits) -> current in hits },
                                    modifier = Modifier
                                        .semantics { contentDescription = "Markdown table" }
                                        .clickable(onClickLabel = "Open table") { onExpand(index) },
                                )
                            }
                            CopyButton({ tableAsText(segment) }, "Copy table", Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Arrow pointing left, drawn here as the app does not depend on the material icons library. */
private val BackArrow: ImageVector by lazy {
    ImageVector.Builder(name = "BackArrow", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
        .build()
}
