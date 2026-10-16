package io.github.mikolajkakol.easymarkdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Renders the inline markdown used in table cells: `**bold**`, `*italic*`, `~~strike~~`, `` `code` ``,
 * `[links](url)`, `<br>` line breaks and backslash escapes. Unmatched markers are shown as typed.
 */
fun inlineMarkdown(
    text: String,
    codeStyle: SpanStyle = SpanStyle(),
    linkStyles: TextLinkStyles? = null,
): AnnotatedString = buildAnnotatedString { appendInline(text, codeStyle, linkStyles) }

private val lineBreak = Regex("""^<br\s*/?>""", RegexOption.IGNORE_CASE)

private fun AnnotatedString.Builder.appendInline(text: String, codeStyle: SpanStyle, linkStyles: TextLinkStyles?) {
    fun inner(part: String) = appendInline(part, codeStyle, linkStyles)

    var i = 0
    while (i < text.length) {
        val c = text[i]
        val rest = text.substring(i)
        val br = lineBreak.find(rest)
        when {
            c == '\\' && i + 1 < text.length && !text[i + 1].isLetterOrDigit() -> {
                append(text[i + 1])
                i += 2
            }
            br != null -> {
                append('\n')
                i += br.value.length
            }
            c == '`' && text.indexOf('`', i + 1) > i -> {
                val end = text.indexOf('`', i + 1)
                withStyle(codeStyle) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            (rest.startsWith("**") || rest.startsWith("__")) && closing(text, i, 2) > 0 -> {
                val end = closing(text, i, 2)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inner(text.substring(i + 2, end)) }
                i = end + 2
            }
            rest.startsWith("~~") && closing(text, i, 2) > 0 -> {
                val end = closing(text, i, 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { inner(text.substring(i + 2, end)) }
                i = end + 2
            }
            (c == '*' || (c == '_' && (i == 0 || !text[i - 1].isLetterOrDigit()))) && closing(text, i, 1) > 0 -> {
                val end = closing(text, i, 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inner(text.substring(i + 1, end)) }
                i = end + 1
            }
            c == '[' && link(text, i) != null -> {
                val (label, url, end) = link(text, i)!!
                withLink(LinkAnnotation.Url(url, linkStyles)) { inner(label) }
                i = end
            }
            else -> {
                append(c)
                i++
            }
        }
    }
}

/** Index of the marker closing the one of [length] chars at [start], or -1. Requires non-empty content. */
private fun closing(text: String, start: Int, length: Int): Int {
    val marker = text.substring(start, start + length)
    val end = text.indexOf(marker, start + length + 1)
    if (end < 0) return -1
    // A single `*` must not close on the first half of a `**`.
    if (length == 1 && end + 1 < text.length && text[end + 1] == marker[0]) return -1
    return end
}

/** Parses `[label](url)` at [start] into label, url and the index after it. */
private fun link(text: String, start: Int): Triple<String, String, Int>? {
    val labelEnd = text.indexOf("](", start)
    if (labelEnd < 0) return null
    val urlEnd = text.indexOf(')', labelEnd + 2)
    if (urlEnd < 0) return null
    val url = text.substring(labelEnd + 2, urlEnd).trim()
    if (url.isEmpty() || url.contains(' ')) return null
    return Triple(text.substring(start + 1, labelEnd), url, urlEnd + 1)
}
