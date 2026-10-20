package io.github.mikolajkakol.easymarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders mermaid diagrams with the JavaFX WebView and draws whole screens, saving them to
 * `build/desktop-screenshots`. Needs a display, like the macOS CI runner.
 */
class DesktopRenderingTest {
    private val outDir = File("build/desktop-screenshots").apply { mkdirs() }

    @Test
    fun rendersMermaidDiagram() = runBlocking {
        val code = "flowchart LR\n    A[Open file] --> B{Is markdown?}\n    B -- Yes --> C[Render]"
        val diagram = assertNotNull(MermaidSnapshotter.render(code, darkTheme = false, maxWidth = 600, scale = 2f))
        assertTrue(diagram.width in 1..600 && diagram.height > 0, "size ${diagram.width}x${diagram.height}")
        val pixels = diagram.image.toPixelMap()
        val opaque = (0 until pixels.width step 4).sumOf { x ->
            (0 until pixels.height step 4).count { y -> pixels[x, y].alpha > 0.5f }
        }
        assertTrue(opaque > 100, "diagram is blank")
    }

    @Test
    fun screenshots() {
        screenshot("app_light", height = 3000) { App(SampleDocument, onOpenFile = {}, darkTheme = false) }
        screenshot("app_dark", height = 3000) { App(SampleDocument, onOpenFile = {}, darkTheme = true) }
        val flowchart = splitDocument(SampleDocument.content).filterIsInstance<DocumentSegment.Mermaid>().first()
        screenshot("diagram_full_screen", height = 700) { DiagramScreen(flowchart.code, darkTheme = false, onClose = {}) }
    }

    /** Draws frames for a while, so diagrams rendered in the background show up. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun screenshot(name: String, height: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = 1640, height = height * 2, density = Density(2f), content = content)
        try {
            var time = 0L
            repeat(60) {
                scene.render(time)
                time += 250_000_000L
                Thread.sleep(250)
            }
            val image = scene.render(time)
            File(outDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
