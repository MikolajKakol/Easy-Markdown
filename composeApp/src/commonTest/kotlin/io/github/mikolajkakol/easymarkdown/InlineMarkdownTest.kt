package io.github.mikolajkakol.easymarkdown

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineMarkdownTest {

    @Test
    fun stripsMarkersAndKeepsText() {
        assertEquals(
            "bold code link italic strike a|b\nnext",
            inlineMarkdown("**bold** `code` [link](https://example.com) *italic* ~~strike~~ a\\|b<br>next").text,
        )
    }

    @Test
    fun appliesBoldAndLink() {
        val text = inlineMarkdown("**bold** [link](https://example.com)")
        val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("bold", text.text.substring(bold.start, bold.end))
        val link = text.getLinkAnnotations(0, text.length).single()
        assertEquals("https://example.com", (link.item as LinkAnnotation.Url).url)
        assertEquals("link", text.text.substring(link.start, link.end))
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertEquals("2 * 3 = 6, snake_case_name, [not a link]", inlineMarkdown("2 * 3 = 6, snake_case_name, [not a link]").text)
    }
}
