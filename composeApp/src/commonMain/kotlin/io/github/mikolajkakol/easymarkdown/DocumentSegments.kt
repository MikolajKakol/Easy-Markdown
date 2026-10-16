package io.github.mikolajkakol.easymarkdown

/** A piece of a markdown document: either plain markdown or a mermaid diagram. */
sealed interface DocumentSegment {
    data class Markdown(val text: String) : DocumentSegment
    data class Mermaid(val code: String) : DocumentSegment
}

private val fenceOpen = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")

/**
 * Splits markdown into segments, extracting fenced code blocks whose info string is `mermaid`
 * (e.g. ```` ```mermaid ````). Other fenced code blocks stay part of the markdown, and fences nested
 * inside them are ignored, so a mermaid example inside a longer fence is shown as code.
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
            markdownBuffer.append(line).append('\n')
            i++
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
        if (language == "mermaid") {
            flushMarkdown()
            segments += DocumentSegment.Mermaid(lines.subList(i + 1, end).joinToString("\n"))
        } else {
            for (j in i..minOf(end, lines.size - 1)) markdownBuffer.append(lines[j]).append('\n')
        }
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
