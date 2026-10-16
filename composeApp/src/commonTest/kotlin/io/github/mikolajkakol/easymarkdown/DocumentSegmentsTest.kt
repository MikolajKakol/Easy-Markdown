package io.github.mikolajkakol.easymarkdown

import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentSegmentsTest {

    @Test
    fun plainMarkdownIsSingleSegment() {
        assertEquals(
            listOf(DocumentSegment.Markdown("# Title\n\nText")),
            splitDocument("# Title\n\nText\n"),
        )
    }

    @Test
    fun extractsMermaidBlocks() {
        val doc = "Intro\n\n```mermaid\ngraph TD\n  A-->B\n```\n\nOutro"
        assertEquals(
            listOf(
                DocumentSegment.Markdown("Intro"),
                DocumentSegment.Mermaid("graph TD\n  A-->B"),
                DocumentSegment.Markdown("\nOutro"),
            ),
            splitDocument(doc),
        )
    }

    @Test
    fun extractsOtherCodeBlocks() {
        assertEquals(
            listOf(
                DocumentSegment.Markdown("Text"),
                DocumentSegment.Code("kotlin", "val x = 1\n\n  indented"),
                DocumentSegment.Code("", "┌─┐\n└─┘"),
            ),
            splitDocument("Text\n```kotlin\nval x = 1\n\n  indented\n```\n```\n┌─┐\n└─┘\n```"),
        )
    }

    @Test
    fun ignoresMermaidNestedInLongerFence() {
        assertEquals(
            listOf(DocumentSegment.Code("md", "```mermaid\ngraph TD\n```")),
            splitDocument("````md\n```mermaid\ngraph TD\n```\n````"),
        )
    }

    @Test
    fun supportsTildeFencesAndCrlf() {
        assertEquals(
            listOf(DocumentSegment.Mermaid("pie\n  \"A\" : 1")),
            splitDocument("~~~ Mermaid\r\npie\r\n  \"A\" : 1\r\n~~~\r\n"),
        )
    }

    @Test
    fun unclosedMermaidFenceRunsToEnd() {
        assertEquals(
            listOf(DocumentSegment.Markdown("Text"), DocumentSegment.Mermaid("graph LR\nA-->B")),
            splitDocument("Text\n```mermaid\ngraph LR\nA-->B"),
        )
    }

    @Test
    fun extractsTables() {
        val doc = "Intro\n\n| A | B | C |\n|:--|:-:|--:|\n| 1 | 2 \\| x |\n| only one |\n\nOutro"
        assertEquals(
            listOf(
                DocumentSegment.Markdown("Intro"),
                DocumentSegment.Table(
                    header = listOf("A", "B", "C"),
                    alignments = listOf(ColumnAlignment.Start, ColumnAlignment.Center, ColumnAlignment.End),
                    rows = listOf(listOf("1", "2 | x", ""), listOf("only one", "", "")),
                ),
                DocumentSegment.Markdown("\nOutro"),
            ),
            splitDocument(doc),
        )
    }

    @Test
    fun tableWithoutOuterPipes() {
        assertEquals(
            listOf(DocumentSegment.Table(listOf("a", "b"), listOf(ColumnAlignment.Start, ColumnAlignment.Start), listOf(listOf("1", "2")))),
            splitDocument("a | b\n--- | ---\n1 | 2"),
        )
    }

    @Test
    fun pipesWithoutDelimiterRowStayMarkdown() {
        val doc = "a | b\nc | d"
        assertEquals(listOf(DocumentSegment.Markdown(doc)), splitDocument(doc))
    }

    @Test
    fun tableInsideCodeFenceStaysCode() {
        assertEquals(
            listOf(DocumentSegment.Code("", "| a | b |\n|---|---|")),
            splitDocument("```\n| a | b |\n|---|---|\n```"),
        )
    }

    @Test
    fun expandsTabsToTabStops() {
        assertEquals("a   b\n    c\nab  d", expandTabs("a\tb\n\tc\nab\td"))
    }
}
