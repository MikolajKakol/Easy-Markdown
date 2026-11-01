package io.github.mikolajkakol.easymarkdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/** A table cell; row 0 is the header. */
data class TableCell(val row: Int, val column: Int)

/**
 * An occurrence of the searched text in segment [segment].
 *
 * [range] indexes the segment's source: the markdown text, the code shown in a code block, the mermaid code,
 * or the raw text of table cell [cell]. A markdown range can span formatting, e.g. `**`, between the matched words.
 * [position] is roughly where in the segment the match is drawn, from 0 (top) to 1 (bottom).
 */
data class SearchMatch(
    val segment: Int,
    val range: IntRange,
    val cell: TableCell? = null,
    val position: Float = 0f,
)

/**
 * Finds text in a document as it is shown: markdown is searched without its markup (link targets, `**`,
 * `#` and the like), so a query can span formatting. Matching ignores case.
 */
class DocumentSearch(segments: List<DocumentSegment>) {
    private val searchables = segments.map { segment ->
        when (segment) {
            is DocumentSegment.Markdown -> Searchable.Markdown(segment.text, visibleText(segment.text))
            is DocumentSegment.Code -> Searchable.Plain(expandTabs(segment.code))
            is DocumentSegment.Mermaid -> Searchable.Plain(segment.code)
            is DocumentSegment.Table -> Searchable.Cells(listOf(segment.header) + segment.rows)
        }
    }

    fun find(query: String): List<SearchMatch> {
        if (query.isEmpty()) return emptyList()
        return searchables.flatMapIndexed { index, searchable ->
            when (searchable) {
                is Searchable.Markdown -> occurrences(searchable.visible.text, query).map { found ->
                    val offsets = searchable.visible.offsets
                    val range = offsets[found.first]..offsets[found.last]
                    SearchMatch(index, range, position = lineFraction(searchable.source, range.first))
                }
                is Searchable.Plain -> occurrences(searchable.text, query).map { range ->
                    SearchMatch(index, range, position = lineFraction(searchable.text, range.first))
                }
                is Searchable.Cells -> searchable.rows.flatMapIndexed { r, row ->
                    row.flatMapIndexed { c, cell ->
                        occurrences(cell, query).map { range ->
                            SearchMatch(index, range, TableCell(r, c), position = r.toFloat() / searchable.rows.size)
                        }
                    }
                }
            }
        }
    }

    private sealed interface Searchable {
        class Markdown(val source: String, val visible: VisibleText) : Searchable
        class Plain(val text: String) : Searchable
        class Cells(val rows: List<List<String>>) : Searchable
    }
}

/** Non-overlapping occurrences of [query] in [text], ignoring case. */
private fun occurrences(text: String, query: String): List<IntRange> = buildList {
    var from = 0
    while (true) {
        val start = text.indexOf(query, from, ignoreCase = true)
        if (start < 0) break
        add(start until start + query.length)
        from = start + query.length
    }
}

private fun lineFraction(text: String, offset: Int): Float {
    val lines = text.count { it == '\n' } + 1
    val line = (0 until offset.coerceAtMost(text.length)).count { text[it] == '\n' }
    return line.toFloat() / lines
}

/** The text a markdown document shows; [offsets] maps each of its characters to the source. */
class VisibleText(val text: String, val offsets: IntArray)

/** Markup tokens drawn as their own text. */
internal val textPunctuation: Set<IElementType> = setOf(
    MarkdownTokenTypes.SINGLE_QUOTE,
    MarkdownTokenTypes.DOUBLE_QUOTE,
    MarkdownTokenTypes.LPAREN,
    MarkdownTokenTypes.RPAREN,
    MarkdownTokenTypes.LBRACKET,
    MarkdownTokenTypes.RBRACKET,
    MarkdownTokenTypes.LT,
    MarkdownTokenTypes.GT,
    MarkdownTokenTypes.COLON,
    MarkdownTokenTypes.EXCLAMATION_MARK,
)

/** Elements whose text is not shown as text, or not passed through the annotator that highlights matches. */
private val hiddenElements = setOf(
    MarkdownElementTypes.LINK_DESTINATION,
    MarkdownElementTypes.LINK_TITLE,
    MarkdownElementTypes.LINK_DEFINITION,
    MarkdownElementTypes.IMAGE,
    MarkdownElementTypes.AUTOLINK,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.HTML_BLOCK,
)

fun visibleText(markdown: String): VisibleText {
    val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    val text = StringBuilder()
    val offsets = ArrayList<Int>()

    fun visit(node: ASTNode) {
        when {
            node.type in hiddenElements -> Unit
            node.type == MarkdownTokenTypes.TEXT -> for (i in node.startOffset until node.endOffset) {
                text.append(markdown[i])
                offsets += i
            }
            node.type == MarkdownTokenTypes.WHITE_SPACE || node.type == MarkdownTokenTypes.EOL -> {
                // Runs of whitespace and line breaks are drawn as a single space.
                if (text.isNotEmpty() && text.last() != ' ') {
                    text.append(' ')
                    offsets += node.startOffset
                }
            }
            node.type in textPunctuation -> for (i in node.startOffset until node.endOffset) {
                text.append(markdown[i])
                offsets += i
            }
            // Only a link's text is shown, without its brackets.
            node.type == MarkdownElementTypes.INLINE_LINK || node.type == MarkdownElementTypes.FULL_REFERENCE_LINK ->
                node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }?.let { linkText ->
                    linkText.children.drop(1).dropLast(1).forEach(::visit)
                }
            node.type == MarkdownElementTypes.SHORT_REFERENCE_LINK ->
                node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }?.let { label ->
                    label.children.drop(1).dropLast(1).forEach(::visit)
                }
            else -> node.children.forEach(::visit)
        }
    }
    visit(tree)
    return VisibleText(text.toString(), offsets.toIntArray())
}
