package io.github.mikolajkakol.easymarkdown

import kotlin.test.Test
import kotlin.test.assertEquals

class TableAsTextTest {

    @Test
    fun rowsAreTabSeparatedWithFormattingStripped() {
        val table = splitDocument(
            """
            | Name | Notes |
            |------|-------|
            | **Bold** | `code` and [link](https://example.com) |
            | a\|b | one<br>two |
            """.trimIndent(),
        ).single() as DocumentSegment.Table

        assertEquals("Name\tNotes\nBold\tcode and link\na|b\tone two", tableAsText(table))
    }
}
