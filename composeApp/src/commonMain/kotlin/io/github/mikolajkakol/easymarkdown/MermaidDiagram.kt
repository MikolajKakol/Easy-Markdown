package io.github.mikolajkakol.easymarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Renders a mermaid diagram from its source code. */
@Composable
expect fun MermaidDiagram(code: String, darkTheme: Boolean, modifier: Modifier = Modifier)
