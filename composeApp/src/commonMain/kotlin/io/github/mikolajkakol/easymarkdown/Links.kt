package io.github.mikolajkakol.easymarkdown

/** File extensions of documents opened in the app when a link points to them. */
val markdownExtensions = setOf("md", "markdown", "mdown", "mkd", "txt")

/** A link to a file next to the shown document, e.g. `other.md`, `../docs/guide.md#setup` or `file:///notes.md`. */
data class LocalLink(
    /** Decoded path, relative to the shown document's folder unless [absolute]. */
    val path: String,
    val absolute: Boolean,
) {
    val isMarkdown: Boolean get() = path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in markdownExtensions
}

/** Returns the file [link] points to, or null for a web link, mail link or an anchor in the same document. */
fun parseLocalLink(link: String): LocalLink? {
    val target = link.trim().substringBefore('#').substringBefore('?')
    if (target.isEmpty()) return null
    val scheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(target)?.groupValues?.get(1)
    val path = when {
        scheme == null -> target
        scheme.equals("file", ignoreCase = true) -> target.substring(scheme.length + 1).replace(Regex("^//[^/]*"), "")
        // A Windows drive letter, as in `C:/notes/a.md`.
        scheme.length == 1 -> target
        else -> return null
    }.replace('\\', '/').let(::percentDecode)
    if (path.isEmpty()) return null
    val absolute = path.startsWith("/") || Regex("^[a-zA-Z]:/").containsMatchIn(path)
    return LocalLink(path, absolute)
}

/**
 * Resolves [link] against [baseDirectory], removing `.` and `..` segments.
 * Paths use `/`; a leading `/` or drive letter of either is kept.
 */
fun resolvePath(baseDirectory: String, link: LocalLink): String {
    val joined = if (link.absolute) link.path else baseDirectory.trimEnd('/') + "/" + link.path
    val root = Regex("^([a-zA-Z]:)?/").find(joined)?.value ?: ""
    val segments = ArrayDeque<String>()
    for (segment in joined.substring(root.length).split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty() && segments.last() != "..") segments.removeLast() else if (root.isEmpty()) segments.addLast("..")
            else -> segments.addLast(segment)
        }
    }
    return root + segments.joinToString("/")
}

private fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val hex = if (c == '%' && i + 2 <= text.lastIndex) text.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes += hex.toByte()
            i += 3
        } else {
            c.toString().encodeToByteArray().forEach { bytes += it }
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}
