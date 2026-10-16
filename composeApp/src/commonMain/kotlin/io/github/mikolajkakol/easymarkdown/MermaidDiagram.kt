package io.github.mikolajkakol.easymarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Renders a mermaid diagram from its source code.
 *
 * Inline ([zoomable] = false) the diagram fits the available width and sizes its height to the content.
 * With [zoomable] = true it fills the given space and supports pinch-to-zoom and panning.
 */
@Composable
expect fun MermaidDiagram(code: String, darkTheme: Boolean, zoomable: Boolean, modifier: Modifier = Modifier)

fun interface MermaidRenderer {
    @Composable
    fun Render(code: String, darkTheme: Boolean, zoomable: Boolean, modifier: Modifier)
}

/** Allows replacing the platform renderer, e.g. in snapshot tests where WebView is unavailable. */
val LocalMermaidRenderer = staticCompositionLocalOf {
    MermaidRenderer { code, darkTheme, zoomable, modifier -> MermaidDiagram(code, darkTheme, zoomable, modifier) }
}

/** Handles the system back gesture while [enabled]. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
