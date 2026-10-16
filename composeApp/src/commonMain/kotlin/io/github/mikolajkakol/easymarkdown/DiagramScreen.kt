package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Full screen view of a single diagram with pinch-to-zoom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramScreen(code: String, darkTheme: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Diagram") },
                actions = { TextButton(onClick = onClose) { Text("Close") } },
            )
        },
    ) { padding ->
        LocalMermaidRenderer.current.Render(
            code = code,
            darkTheme = darkTheme,
            zoomable = true,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
