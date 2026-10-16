package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests recorded with Roborazzi.
 *
 * Record: `./gradlew :composeApp:recordRoborazziDebug`, verify: `./gradlew :composeApp:verifyRoborazziDebug`.
 * WebView does not render under Robolectric, so mermaid blocks are drawn as a placeholder showing their source.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
class AppSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sampleDocumentLight() = snapshot("sample_light", waitForText = "Text formatting") {
        App(document = SampleDocument, onOpenFile = {}, onShowSample = {}, darkTheme = false)
    }

    @Test
    fun sampleDocumentDark() = snapshot("sample_dark", waitForText = "Text formatting") {
        App(document = SampleDocument, onOpenFile = {}, onShowSample = {}, darkTheme = true)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun openingError() = snapshot("opening_error", waitForText = "Cannot open file") {
        App(
            document = OpenedDocument(title = "Error", content = "# Cannot open file\n\n`Permission denied`"),
            onOpenFile = {},
            onShowSample = null,
            darkTheme = false,
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun diagramFullScreen() = snapshot("diagram_fullscreen", waitForText = "Diagram") {
        MaterialTheme {
            DiagramScreen(code = "graph TD\n  A --> B", darkTheme = false, onClose = {})
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun tableFullScreen() = snapshot("table_fullscreen", waitForText = "Compose Multiplatform") {
        MaterialTheme {
            val table = splitDocument(SampleDocument.content).filterIsInstance<DocumentSegment.Table>().last()
            TableScreen(table, onClose = {})
        }
    }

    /** Markdown is parsed asynchronously, so wait for [waitForText] before capturing. */
    private fun snapshot(name: String, waitForText: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalMermaidRenderer provides PlaceholderMermaidRenderer) {
                content()
            }
        }
        composeRule.waitUntilAtLeastOneExists(hasText(waitForText, substring = true), timeoutMillis = 10_000)
        composeRule.onRoot().captureRoboImage("snapshots/$name.png")
    }
}

private val PlaceholderMermaidRenderer = MermaidRenderer { code, _, _, modifier ->
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text("mermaid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}
