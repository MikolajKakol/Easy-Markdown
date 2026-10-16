package io.github.mikolajkakol.easymarkdown

/** A piece of a markdown document: plain markdown, a mermaid diagram, a code block or a table. */
sealed interface DocumentSegment {
    data class Markdown(val text: String) : DocumentSegment
    data class Mermaid(val code: String) : DocumentSegment

    /** A fenced code block (other than mermaid); shown unwrapped so ASCII art keeps its alignment. */
    data class Code(val language: String, val code: String) : DocumentSegment

    /** A GFM table. Every row has exactly as many cells as [header]; cells hold raw inline markdown. */
    data class Table(
        val header: List<String>,
        val alignments: List<ColumnAlignment>,
        val rows: List<List<String>>,
    ) : DocumentSegment
}

enum class ColumnAlignment { Start, Center, End }

private val fenceOpen = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")

/**
 * Splits markdown into segments: fenced code blocks (```` ```mermaid ```` ones become diagrams) and
 * top-level GFM tables are extracted, everything else stays markdown. Anything inside a fence is code,
 * so a mermaid example or a table inside a longer fence is shown as code.
 */
fun splitDocument(markdown: String): List<DocumentSegment> {
    val segments = mutableListOf<DocumentSegment>()
    val markdownBuffer = StringBuilder()
    val lines = markdown.replace("\r\n", "\n").split("\n")

    fun flushMarkdown() {
        if (markdownBuffer.isNotBlank()) segments += DocumentSegment.Markdown(markdownBuffer.toString().trimEnd('\n'))
        markdownBuffer.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val open = fenceOpen.matchEntire(line)
        if (open == null) {
            val table = parseTable(lines, i)
            if (table != null) {
                flushMarkdown()
                segments += table.first
                i = table.second
            } else {
                markdownBuffer.append(line).append('\n')
                i++
            }
            continue
        }
        val fence = open.groupValues[1]
        val info = open.groupValues[2].trim()
        // Backtick fences may not contain backticks in their info string.
        if (fence[0] == '`' && info.contains('`')) {
            markdownBuffer.append(line).append('\n')
            i++
            continue
        }
        val closeIndex = (i + 1 until lines.size).firstOrNull { isClosingFence(lines[it], fence) }
        val end = closeIndex ?: lines.size
        val language = info.substringBefore(' ').lowercase()
        val body = lines.subList(i + 1, end).joinToString("\n")
        flushMarkdown()
        segments += if (language == "mermaid") DocumentSegment.Mermaid(body) else DocumentSegment.Code(language, body)
        i = end + 1
    }
    flushMarkdown()
    return segments
}

private fun isClosingFence(line: String, openFence: String): Boolean {
    val trimmed = line.trimEnd()
    val indent = trimmed.length - trimmed.trimStart().length
    if (indent > 3) return false
    val body = trimmed.trimStart()
    return body.length >= openFence.length && body.all { it == openFence[0] }
}

private val delimiterCell = Regex("""^:?-+:?$""")

/** Parses a table starting at [start]; returns it with the index of the first line after it, or null. */
private fun parseTable(lines: List<String>, start: Int): Pair<DocumentSegment.Table, Int>? {
    if (start + 1 >= lines.size || !isTableLine(lines[start]) || !isTableLine(lines[start + 1])) return null
    val header = splitRow(lines[start])
    val delimiters = splitRow(lines[start + 1])
    if (delimiters.size != header.size || !delimiters.all { delimiterCell.matches(it) }) return null
    val alignments = delimiters.map {
        when {
            it.startsWith(':') && it.endsWith(':') -> ColumnAlignment.Center
            it.endsWith(':') -> ColumnAlignment.End
            else -> ColumnAlignment.Start
        }
    }
    val rows = mutableListOf<List<String>>()
    var i = start + 2
    while (i < lines.size && isTableLine(lines[i]) && fenceOpen.matchEntire(lines[i]) == null) {
        val cells = splitRow(lines[i])
        rows += List(header.size) { cells.getOrElse(it) { "" } }
        i++
    }
    return DocumentSegment.Table(header, alignments, rows) to i
}

private fun isTableLine(line: String): Boolean =
    line.isNotBlank() && line.length - line.trimStart().length <= 3 && line.contains('|')

/** Splits a table row on unescaped pipes, dropping the optional leading and trailing pipe. */
private fun splitRow(line: String): List<String> {
    var row = line.trim()
    if (row.startsWith('|')) row = row.substring(1)
    if (row.endsWith('|') && !row.endsWith("\\|")) row = row.dropLast(1)
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var i = 0
    while (i < row.length) {
        when {
            row[i] == '\\' && i + 1 < row.length && row[i + 1] == '|' -> { cell.append('|'); i += 2 }
            row[i] == '|' -> { cells += cell.toString().trim(); cell.clear(); i++ }
            else -> { cell.append(row[i]); i++ }
        }
    }
    cells += cell.toString().trim()
    return cells
}
