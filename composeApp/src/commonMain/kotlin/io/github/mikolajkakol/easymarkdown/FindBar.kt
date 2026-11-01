package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownTokenTypes

/** The find bar's state: whether it is shown, the query and which match is selected. */
@Stable
class SearchState {
    var visible by mutableStateOf(false)
        private set
    var field by mutableStateOf(TextFieldValue())
    val query: String get() = field.text

    /** Index of the selected match. */
    var current by mutableIntStateOf(0)
    var matchCount by mutableIntStateOf(0)
        internal set

    /** Incremented to focus the query field. */
    internal var focusRequests by mutableIntStateOf(0)
        private set

    /** Shows the find bar and focuses it, selecting the previous query. */
    fun open() {
        visible = true
        field = field.copy(selection = TextRange(0, field.text.length))
        focusRequests++
    }

    fun close() {
        visible = false
    }

    fun next() = step(1)

    fun previous() = step(-1)

    private fun step(by: Int) {
        if (!visible) return open()
        if (matchCount > 0) current = (current + by).mod(matchCount)
    }
}

@Composable
fun rememberSearchState() = remember { SearchState() }

/** A match to highlight; [current] is the selected one. */
data class Highlight(val range: IntRange, val current: Boolean)

fun highlightStyle(current: Boolean) = SpanStyle(
    background = if (current) Color(0xFFFF9632) else Color(0xFFFFEB3B),
    color = Color.Black,
)

@Composable
fun FindBar(search: SearchState, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(search.focusRequests) { focusRequester.requestFocus() }
    PlatformBackHandler(enabled = true, onBack = search::close)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = search.field,
            onValueChange = {
                if (it.text != search.query) search.current = 0
                search.field = it
            },
            placeholder = { Text("Find in document") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search.next() }),
            suffix = {
                if (search.query.isNotEmpty()) {
                    Text(
                        if (search.matchCount == 0) "No matches" else "${search.current + 1} of ${search.matchCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown || (it.key != Key.Enter && it.key != Key.NumPadEnter)) {
                        return@onPreviewKeyEvent false
                    }
                    if (it.isShiftPressed) search.previous() else search.next()
                    true
                },
        )
        TextButton(
            onClick = search::previous,
            enabled = search.matchCount > 0,
            modifier = Modifier.semantics { contentDescription = "Previous match" },
        ) { Text("↑") }
        TextButton(
            onClick = search::next,
            enabled = search.matchCount > 0,
            modifier = Modifier.semantics { contentDescription = "Next match" },
        ) { Text("↓") }
        TextButton(onClick = search::close) { Text("Done") }
    }
}

/** Highlights [highlights], given as ranges of the markdown source, in text drawn by the markdown renderer. */
fun searchAnnotator(highlights: List<Highlight>): MarkdownAnnotator = markdownAnnotator(
    annotate = annotate@{ content, child ->
        val start = child.startOffset
        val end = child.endOffset
        val hits = highlights.filter { it.range.first < end && it.range.last >= start }
        if (hits.isEmpty()) return@annotate false
        when (child.type) {
            MarkdownTokenTypes.TEXT -> {
                val raw = content.substring(start, end)
                val shown = child.getUnescapedTextInNode(content)
                if (shown != raw) {
                    // Escapes shift the offsets, so the whole token is highlighted.
                    withStyle(highlightStyle(hits.any { it.current })) { append(shown) }
                } else {
                    append(highlighted(raw, start, hits))
                }
                true
            }
            // Mirrors the renderer, which draws no space at the start of a text.
            MarkdownTokenTypes.WHITE_SPACE -> {
                if (length > 0) withStyle(highlightStyle(hits.any { it.current })) { append(' ') }
                true
            }
            in textPunctuation -> {
                withStyle(highlightStyle(hits.any { it.current })) { append(content.substring(start, end)) }
                true
            }
            else -> false
        }
    },
)

/** [text], which starts at [offset] of the source, with [highlights] (source ranges) styled. */
fun highlighted(text: String, offset: Int, highlights: List<Highlight>): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    for (highlight in highlights) {
        val from = (highlight.range.first - offset).coerceAtLeast(0)
        val to = (highlight.range.last + 1 - offset).coerceAtMost(text.length)
        if (from < to) builder.addStyle(highlightStyle(highlight.current), from, to)
    }
    return builder.toAnnotatedString()
}
