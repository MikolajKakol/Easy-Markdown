package io.github.mikolajkakol.easymarkdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentSearchTest {
    private fun find(markdown: String, query: String) = DocumentSearch(splitDocument(markdown)).find(query)

    @Test
    fun visibleTextDropsMarkup() {
        val text = visibleText("# Title\n\nSome **bold** and [a link](https://example.com \"title\").").text
        assertEquals("Title Some bold and a link.", text)
    }

    @Test
    fun matchesIgnoringCaseAcrossFormatting() {
        val markdown = "Some **bold** text"
        val matches = find(markdown, "BOLD TEXT")
        assertEquals(1, matches.size)
        assertEquals("bold** text", markdown.substring(matches[0].range))
    }

    @Test
    fun skipsLinkTargets() {
        assertTrue(find("[docs](https://example.com/docs)", "example").isEmpty())
        assertEquals(1, find("[docs](https://example.com/docs)", "docs").size)
    }

    @Test
    fun findsEveryOccurrence() {
        assertEquals(3, find("aa aa\n\naa", "aa").size)
        assertEquals(2, find("aaaa", "aa").size)
    }

    @Test
    fun findsInCodeTablesAndDiagrams() {
        val markdown = """
            Intro

            | Name | Value |
            |------|-------|
            | key  | found |

            ```kotlin
            val found = true
            ```

            ```mermaid
            graph TD
              A[found] --> B
            ```
        """.trimIndent()
        val matches = find(markdown, "found")
        val segments = splitDocument(markdown)
        assertEquals(
            listOf(DocumentSegment.Table::class, DocumentSegment.Code::class, DocumentSegment.Mermaid::class),
            matches.map { segments[it.segment]::class },
        )
        assertEquals(TableCell(row = 1, column = 1), matches[0].cell)
        assertEquals("found", (segments[2] as DocumentSegment.Code).code.substring(matches[1].range))
    }

    @Test
    fun emptyQueryFindsNothing() {
        assertTrue(find("anything", "").isEmpty())
    }
}
