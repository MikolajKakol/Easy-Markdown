package io.github.mikolajkakol.easymarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Renders a mermaid diagram from its source code. */
@Composable
expect fun MermaidDiagram(code: String, darkTheme: Boolean, modifier: Modifier = Modifier)

fun interface MermaidRenderer {
    @Composable
    fun Render(code: String, darkTheme: Boolean, modifier: Modifier)
}

/** Allows replacing the platform renderer, e.g. in snapshot tests where WebView is unavailable. */
val LocalMermaidRenderer = staticCompositionLocalOf {
    MermaidRenderer { code, darkTheme, modifier -> MermaidDiagram(code, darkTheme, modifier) }
}
