package io.github.mikolajkakol.easymarkdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinksTest {

    @Test
    fun webAndMailLinksAreNotLocal() {
        assertNull(parseLocalLink("https://mermaid.js.org"))
        assertNull(parseLocalLink("mailto:someone@example.com"))
        assertNull(parseLocalLink("#section"))
        assertNull(parseLocalLink(""))
    }

    @Test
    fun relativeLinksDropAnchorAndQuery() {
        assertEquals(LocalLink("docs/guide.md", absolute = false), parseLocalLink("docs/guide.md#setup"))
        assertEquals(LocalLink("other.md", absolute = false), parseLocalLink("other.md?plain=1"))
    }

    @Test
    fun percentEncodedCharactersAreDecoded() {
        assertEquals(LocalLink("my notes/zażółć.md", absolute = false), parseLocalLink("my%20notes/za%C5%BC%C3%B3%C5%82%C4%87.md"))
    }

    @Test
    fun fileUrisAndDrivePathsAreAbsolute() {
        assertEquals(LocalLink("/home/me/a.md", absolute = true), parseLocalLink("file:///home/me/a.md"))
        assertEquals(LocalLink("C:/notes/a.md", absolute = true), parseLocalLink("C:\\notes\\a.md"))
        assertEquals(LocalLink("/notes/a.md", absolute = true), parseLocalLink("/notes/a.md"))
    }

    @Test
    fun markdownExtensionsAreRecognised() {
        assertTrue(parseLocalLink("README.MD")!!.isMarkdown)
        assertTrue(parseLocalLink("notes/a.markdown")!!.isMarkdown)
        assertFalse(parseLocalLink("image.png")!!.isMarkdown)
        assertFalse(parseLocalLink("folder.md/file")!!.isMarkdown)
    }

    @Test
    fun pathsResolveAgainstTheDocumentFolder() {
        assertEquals("/home/me/docs/b.md", resolvePath("/home/me/docs", parseLocalLink("b.md")!!))
        assertEquals("/home/me/docs/b.md", resolvePath("/home/me/docs/", parseLocalLink("./b.md")!!))
        assertEquals("/home/me/c.md", resolvePath("/home/me/docs", parseLocalLink("../c.md")!!))
        assertEquals("/c.md", resolvePath("/home", parseLocalLink("../../../c.md")!!))
        assertEquals("C:/notes/c.md", resolvePath("C:/notes/sub", parseLocalLink("../c.md")!!))
        assertEquals("/abs/a.md", resolvePath("/home/me", parseLocalLink("/abs/./x/../a.md")!!))
    }
}
